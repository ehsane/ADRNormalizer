package com.rnlp.adr.normalizer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.TransformerConfigurationException;

import octopus.semantic.similarity.HybridBAMSR;

import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.xml.sax.SAXException;

import rainbownlp.core.Phrase;
import rainbownlp.core.PhraseLink;
import rainbownlp.machinelearning.MLExample;
import rainbownlp.util.FileUtil;

public class UMLSManager {
	private static final String ISA_RELATION_FILE_PATH = "/media/NewHard_/corpora/umls/concepts.isa";
	private static final String UMLS_DEF_FILE_PATH = "/media/NewHard_/corpora/umls/mmsys/2014AB/META/MRCONSO.RRF.aa";
	private static final String GRAPH_FILE_PATH = "adr_concepts.graph";
	private static final String ADR_CONCEPTS_FILE_PATH = "data/conceptNames.tsv";
	private static final String ADR_CONCEPTS_FILE_PATH_DEF = "data/conceptNames.def";
	static DirectedGraph<String, DefaultEdge> adrUMLSConcepts;
	static HashMap<String, List<String>> adrUMLSConceptsDefinition = new HashMap<String, List<String>>();
	static Set<String> rootNodes = new HashSet<String>();
	static Set<String> adrConceptsSet = new HashSet<String>();
	
	public static void main(String[] args) throws Exception{
//		createIsaFile();
		createGraph();
		createDefinitions();
		loadGraph();
		verifyGraph();
	}
	
	private static void verifyGraph() {
		CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<String, DefaultEdge>(adrUMLSConcepts);
		if(cycleDetector.detectCycles()){
			System.out.println("Error: Graph has cycle!");
		}
		int missingNodes = 0;
		for(String adrConcept: adrConceptsSet){
			if(!adrUMLSConcepts.containsVertex(adrConcept)){
				System.out.println("Error: concept missing in graph: "+adrConcept);
				missingNodes++;
			}
//			else{
//				if(cycleDetector.detectCyclesContainingVertex(adrConcept)){
//					System.out.println("Error: concept is in cycle: "+adrConcept);
//					Set<String> cycle = cycleDetector.findCyclesContainingVertex(adrConcept);
//					for(String cycleNode : cycle){
//						System.out.println("Cycle Node: "+cycleNode);
//					}
//				}
//			}
		}
		System.out.println("Total number of ADR concepts: "+adrConceptsSet.size());
		System.out.println("Missing concepts in graph count: "+missingNodes);
		
	}

	public UMLSManager() throws Exception{
		loadGraph();
		loadDefinitions();
		
		List<String> adrConcepts = FileUtil.loadLineByLine(ADR_CONCEPTS_FILE_PATH);
		for(String adrCLine : adrConcepts){
			String root = findRoot(adrCLine.split("\t")[0].toLowerCase(), new ArrayList<String>());
			rootNodes.add(root);
		}
	}
	private String findRoot(String conceptId, List<String> path) throws Exception {
		if(!adrUMLSConcepts.containsVertex(conceptId)) return null;
		Set<DefaultEdge> inLinks = adrUMLSConcepts.incomingEdgesOf(conceptId);
		if(inLinks == null || inLinks.isEmpty()) return conceptId;
		if(inLinks.size()>1) System.out.println("More than one income links: "+conceptId);
		DefaultEdge e = ((DefaultEdge)inLinks.toArray()[0]);
		if(adrUMLSConcepts.getEdgeSource(e).equals(conceptId))
			throw(new Exception("self link: "+conceptId));
		String parent = adrUMLSConcepts.getEdgeSource(e);
		if(path.contains(parent))//loop?
			return conceptId;
		else{
			path.add(conceptId);
			return findRoot(parent, path);
		}
	}

	public String getMostSimilarConcept(Phrase phrase, HybridBAMSR bamsr, Set<String> currentOptions, String parentNode){
		if(currentOptions==null)
			currentOptions = rootNodes;
		if(currentOptions.isEmpty())
			return parentNode;
		
		String mostSimilarConceptId = null;
		Double bestSum = 0.0;
		try {
			for(String umlsConceptId: currentOptions){
				List<String> defs = adrUMLSConceptsDefinition.get(umlsConceptId);
//				List<String> defs = getDefinitions(umlsConceptId);
				if(defs==null){
					System.out.println("ERROR: no definition for: "+umlsConceptId);
					continue;
				}
				List<SimpleEntry<Double, SimpleEntry<String, String>>> exampleEntries = new ArrayList<SimpleEntry<Double,SimpleEntry<String,String>>>();
				for(String def: defs){
					SimpleEntry<String, String> conceptsPair = new SimpleEntry<String, String>(phrase.getPhraseContent(), def);
					SimpleEntry<Double, SimpleEntry<String, String>> expectedValuePair = new SimpleEntry<Double, SimpleEntry<String,String>>(0.0, conceptsPair);
					exampleEntries.add(expectedValuePair);
				}
				List<MLExample> curConceptExamples = bamsr.createRegressionExamples(exampleEntries, "UMLS_ADR");;
				bamsr.test(curConceptExamples);
				Double simSum = 0.0;
				for(MLExample example: curConceptExamples){
					simSum += Double.valueOf(example.getPredictedClass()); 
				}
				if(simSum>bestSum){
					mostSimilarConceptId = umlsConceptId;
					bestSum = simSum;
				}
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String mostSimilarInChildren = getMostSimilarConcept(phrase, bamsr, getChilds(mostSimilarConceptId), mostSimilarConceptId);
		// altId is umls concept id
		return mostSimilarInChildren;
	}
	private Set<String> getChilds(String conceptId) {
		Set<DefaultEdge> childLinks = adrUMLSConcepts.outgoingEdgesOf(conceptId);
		Set<String> childs = new HashSet<String>();
		for(DefaultEdge e : childLinks){
			childs.add(adrUMLSConcepts.getEdgeTarget(e));
		}
		return childs;
	}

	private static void loadDefinitions() throws IOException {
		BufferedReader br1 = new BufferedReader(
				new FileReader(new File(ADR_CONCEPTS_FILE_PATH_DEF)));
		while (br1.ready()) {
			String defLin = br1.readLine();
			String[] defs = defLin.split("\t")[1].split(",|;|\\|");
			String conceptId = defLin.split("\t")[0];
			List<String> defsList = new ArrayList<String>();
			for(String d : defs) if(!defsList.contains(d.trim())) defsList.add(d.trim());
			adrUMLSConceptsDefinition.put(conceptId, defsList);
		}
		br1.close();
	}
	
	private static List<String> getDefinitions(String pConceptId) throws IOException {
		BufferedReader br1 = new BufferedReader(
				new FileReader(new File(ADR_CONCEPTS_FILE_PATH_DEF)));
		while (br1.ready()) {
			String defLin = br1.readLine();
			String[] defs = defLin.split("\t")[1].split("\\|");
			String cId = defLin.split("\t")[0];
			if(!cId.equals(pConceptId)) continue;
			List<String> defsList = new ArrayList<String>();
			for(String d : defs) defsList.add(d);
			return defsList;
		}
		br1.close();
		return null;
	}
	private static void loadGraph() throws Exception {
		InputStream buffer = new BufferedInputStream(new FileInputStream(GRAPH_FILE_PATH));
	    ObjectInput	 input = new ObjectInputStream(buffer);
	    adrUMLSConcepts = (DirectedGraph<String, DefaultEdge>) input.readObject();
	    input.close();
	    
	    List<String> adrConcepts = FileUtil.loadLineByLine(ADR_CONCEPTS_FILE_PATH);
		for(String adrCLine : adrConcepts){
			adrConceptsSet.add(adrCLine.split("\t")[0].toLowerCase());
		}
		adrConcepts = null;
	}
	private static void createDefinitions() throws IOException {
		// TODO Auto-generated method stub
		BufferedReader br1 = new BufferedReader(
				new FileReader(new File(UMLS_DEF_FILE_PATH)));
		HashMap<String, String> defHashMap = new HashMap<String, String>();
		Pattern p = Pattern.compile("([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([^\\|]*)\\|.*");
		while (br1.ready()) {
			String defLine = br1.readLine().toLowerCase();
			Matcher m = p.matcher(defLine);
			if(m.matches()){
				String conceptId = m.group(1);
				String lang = m.group(2);
				if(!lang.equals("eng") || m.group(7).equals("n")) continue;
				String definition = m.group(15);
				if(adrUMLSConcepts.containsVertex(conceptId)){
					String def = ((defHashMap.get(conceptId) == null)?definition:(defHashMap.get(conceptId)+"|"+definition));
					defHashMap.put(conceptId, def);
				}
			}
		}
		
		File filtered = new File(ADR_CONCEPTS_FILE_PATH_DEF);
		BufferedWriter bw = new BufferedWriter(new FileWriter(filtered));
		
		for(String conceptId: defHashMap.keySet()){
			bw.write(conceptId+"\t"+defHashMap.get(conceptId)+"\n");
		}

		bw.flush();
		bw.close();
		br1.close();
	}
	private static void createGraph() throws IOException, TransformerConfigurationException, SAXException {
		adrUMLSConcepts =  new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
		List<String> adrConcepts = FileUtil.loadLineByLine(ADR_CONCEPTS_FILE_PATH);
		for(String adrCLine : adrConcepts){
			adrConceptsSet.add(adrCLine.split("\t")[0].toLowerCase());
		}
		BufferedReader br1 = new BufferedReader(
				new FileReader(new File(ISA_RELATION_FILE_PATH)));
		int counter = 0;
		while (br1.ready()) {
			String isaLink = br1.readLine();
			String[] parts = isaLink.toLowerCase().split("\t");
			if(!adrConceptsSet.contains(parts[1]) &&
					!adrUMLSConcepts.containsVertex(parts[1])) continue;
			if(parts[1].equals(parts[0])) continue;//no self-link
			adrUMLSConcepts.addVertex(parts[0]);
			adrUMLSConcepts.addVertex(parts[1]);
			if(adrUMLSConcepts.inDegreeOf(parts[1])>0) continue;//only one parent is allowed
			adrUMLSConcepts.addEdge(parts[0], parts[1]); // [1] is [0] so link from [0] to [1]
//			CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<String, DefaultEdge>(adrUMLSConcepts);
//			if(cycleDetector.detectCycles()){
//				adrUMLSConcepts.removeEdge(parts[0], parts[1]);
//			}
			counter++;
			System.out.println("line processed: "+counter);
		}
		br1.close();
		OutputStream buffer = new BufferedOutputStream(new FileOutputStream(GRAPH_FILE_PATH));
	    ObjectOutput output = new ObjectOutputStream(buffer);
	    output.writeObject(adrUMLSConcepts);
	    output.flush();
	    output.close();
	    System.out.println("Graph created:"+GRAPH_FILE_PATH);
	    
	}
	private static void createIsaFile() {
		String umlConceptsFile = "/media/NewHard_/corpora/umls/mmsys/2014AB/META/MRREL.RRF.aa";
		filterNonIsA(umlConceptsFile);
		String umlConceptsFile1 = "/media/NewHard_/corpora/umls/mmsys/2014AB/META/MRREL.RRF.ac";
		filterNonIsA(umlConceptsFile1);
		String umlConceptsFile2 = "/media/NewHard_/corpora/umls/mmsys/2014AB/META/MRREL.RRF.ab";
		filterNonIsA(umlConceptsFile2);
		String umlConceptsFile3 = "/media/NewHard_/corpora/umls/mmsys/2014AB/META/MRREL.RRF.ad";
		filterNonIsA(umlConceptsFile3);
	}
	private static void filterNonIsA(String umlConceptsFile) {
		File f = new File(umlConceptsFile);
		File filtered = new File(ISA_RELATION_FILE_PATH);
		try {
			if(!f.exists()) return;
			BufferedWriter bw = new BufferedWriter(new FileWriter(filtered, true));
			BufferedReader br1 = new BufferedReader(
					new FileReader(f));
			String lastToConcept = "";
			Pattern p = Pattern.compile("([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|([a-zA-Z0-9]*)\\|(([a-zA-Z0-9]|_|\\-)*)\\|.*");
			while (br1.ready()) {
				String line = br1.readLine().toLowerCase();
				Matcher m = p.matcher(line);
				if(m.matches()){
					String toConceptId = m.group(1);
					String relationCode = m.group(4);
					String fromConceptId = m.group(5);
					String relation = m.group(8);
					if(toConceptId==null || toConceptId.equals(""))
						toConceptId = lastToConcept;
					else
						lastToConcept = toConceptId;
					if(relation.equals("isa") || relation.equals("chd") 
							|| relation.equals("mapped_to") || relation.equals("mapped_from")
							|| relation.equals("classified_as"))
						bw.write(toConceptId+"\t"+fromConceptId+"\n");
					else if(relation.equals("par")
							|| relation.equals("classifies"))
						bw.write(fromConceptId+"\t"+toConceptId+"\n");
					else if(relationCode.equals("rq") || relationCode.equals("sib")){
						bw.write(toConceptId+"\t"+fromConceptId+"\n");
					}
				}else{
					System.out.println("Line doesn't match: "+line);
				}
				
					
			}
			br1.close();
			bw.flush();
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(umlConceptsFile+" processed");
		
	}
	public static List<Phrase> getConcepts() {
		// TODO Auto-generated method stub
		return null;
	}

}
