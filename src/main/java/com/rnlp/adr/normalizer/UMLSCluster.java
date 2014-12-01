package com.rnlp.adr.normalizer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
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
import org.jgrapht.graph.DefaultEdge;
import org.xml.sax.SAXException;

import pitt.search.semanticvectors.vectors.Vector;
import rainbownlp.core.Phrase;
import rainbownlp.machinelearning.MLExample;
import rainbownlp.util.FileUtil;

public class UMLSCluster {
	private static final String ISA_RELATION_FILE_PATH = "/media/NewHard_/corpora/umls/concepts.isa";
	private static final String UMLS_DEF_FILE_PATH = "/media/NewHard_/corpora/umls/mmsys/2014AB/META/MRCONSO.RRF.aa";
	private static final String GRAPH_FILE_PATH = "adr_concepts.graph";
	private static final String ADR_CONCEPTS_FILE_PATH = "data/conceptNames.tsv";
	private static final String ADR_CONCEPTS_FILE_PATH_DEF = "data/conceptNames.def";
	private static final int MAX_ROOT_NODE = 4;
	static DirectedGraph<String, DefaultEdge> adrUMLSConcepts;
	static HashMap<String, List<String>> adrUMLSConceptsDefinition = new HashMap<String, List<String>>();
	static Set<String> rootNodes = new HashSet<String>();
	static Set<String> adrConceptsSet = new HashSet<String>();
	
	public static void main(String[] args) throws Exception{
//		List<Vector> termVectors = loadTermVectors();
//		List<Vector> clusteredVectors = cluster(termVectors);
//		while(clusteredVectors.size()>MAX_ROOT_NODE){
//			String[] parts = isaLink.toLowerCase().split("\t");
//			if(!adrConceptsSet.contains(parts[1]) &&
//					!adrUMLSConcepts.containsVertex(parts[1])) continue;
//			if(parts[1].equals(parts[0])) continue;//no self-link
//			adrUMLSConcepts.addVertex(parts[0]);
//			adrUMLSConcepts.addVertex(parts[1]);
//			if(adrUMLSConcepts.inDegreeOf(parts[1])>0) continue;//only one parent is allowed
//			adrUMLSConcepts.addEdge(parts[0], parts[1]); // [1] is [0] so link from [0] to [1]
//		}
//		verifyGraph();
	}
	
	private static List<Vector> cluster(List<Vector> termVectors) {
		// TODO Auto-generated method stub
		return null;
	}

	private static List<Vector> loadTermVectors() {
		// TODO Auto-generated method stub
		return null;
	}

	
	private static void saveGraph() throws Exception {
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
//							|| relation.equals("mapped_to") || relation.equals("mapped_from")
//							|| relation.equals("classified_as")
							)
						bw.write(toConceptId+"\t"+fromConceptId+"\n");
					else if(relation.equals("par")
//							|| relation.equals("classifies")
							)
						bw.write(fromConceptId+"\t"+toConceptId+"\n");
//					else if(relationCode.equals("rq") || relationCode.equals("sib")){
//						bw.write(toConceptId+"\t"+fromConceptId+"\n");
//					}
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
