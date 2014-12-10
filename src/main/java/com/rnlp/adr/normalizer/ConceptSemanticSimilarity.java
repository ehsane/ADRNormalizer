package com.rnlp.adr.normalizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import octopus.semantic.similarity.msr.IMSR;
import octopus.semantic.similarity.msr.vector.MSRLSA;
import octopus.semantic.similarity.resource.TextualCorpusResource;
import octopus.semantic.similarity.resource.TextualCorpusResource.CorpusFormat;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorUtils;

public class ConceptSemanticSimilarity {
	IMSR lsaMSR;
	TextualCorpusResource pubmedSystematicReviews;
	static HashMap<String, List<String>> adrUMLSConceptsDefinition = new HashMap<String, List<String>>();
//	static HashMap<String, List<Vector>> adrUMLSConceptsVectors = new HashMap<String, List<Vector>>();
	static HashMap<Vector, String> adrUMLSConceptsVectors = new HashMap<Vector, String>();
	private static final String UMLS_DEF_FILE_PATH = "/media/NewHard_/corpora/umls/mmsys/2014AB/META/MRCONSO.RRF.aa";
	private static final String ADR_CONCEPTS_VECTORS_FILE_PATH = "data/conceptNames.vectors";
	private static final String ADR_CONCEPTS_FILE_PATH_DEF = "data/conceptNames_onlydirectused.def";
	static Set<String> adrConceptsSet = new HashSet<String>();
	static Vector[] vArr;
	
	public static void main(String[] args) throws Exception{
//		List<String> adrConcepts = FileUtil.loadLineByLine(ADR_CONCEPTS_FILE_PATH);
//		for(String adrCLine : adrConcepts){
//			adrConceptsSet.add(adrCLine.split("\t")[0].toLowerCase());
//		}
//		createDefinitions();
		generateVectors();
	}
	private static void generateVectors() throws Exception {
		ConceptSemanticSimilarity c = new ConceptSemanticSimilarity();
		c.generateVectorsForConcepts();
		
	}
	public ConceptSemanticSimilarity() throws Exception{
		lsaMSR = new MSRLSA();
		pubmedSystematicReviews = new TextualCorpusResource("PubmedSystematicReviews",
				"/media/NewHard_/corpora/pubmed_result_systematic_reviews.txt", CorpusFormat.PUBMED_ABSTRACTS);
		
		loadDefinitions();
		try{
			loadVectors();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	public void generateVectorsForConcepts() throws IOException{
		for(String cId : adrUMLSConceptsDefinition.keySet()){
			for(String def : adrUMLSConceptsDefinition.get(cId)){
				def = def.replaceAll("[^0-9a-zA-Z]+", " ");
				Vector v = pubmedSystematicReviews.getPhraseVector(def, "svd_termvectors.bin", "svd_docvectors.bin", null);
				adrUMLSConceptsVectors.put(v, cId);
			}
		}
		vArr = new Vector[adrUMLSConceptsVectors.size()];
		vArr = adrUMLSConceptsVectors.keySet().toArray(vArr );
		
		BufferedWriter buffer = new BufferedWriter(new FileWriter(ADR_CONCEPTS_VECTORS_FILE_PATH));
		for(Vector v : adrUMLSConceptsVectors.keySet()){
			String conceptId = adrUMLSConceptsVectors.get(v);
			buffer.write(conceptId+"\t\t\t");
			buffer.write(v.writeToString()+"\n");
		}
		buffer.flush();
		buffer.close();
	    
	}
	public void loadVectors() throws ClassNotFoundException, IOException{
	    BufferedReader buffer = new BufferedReader(new FileReader(ADR_CONCEPTS_VECTORS_FILE_PATH));
	    while(buffer.ready()){
	    	String[] lineParts = buffer.readLine().split("\t\t\t");
	    	RealVector v = new RealVector(new float[]{1});
	    	v = v.createZeroVector(lineParts[1].split("\\|").length);
	    	v.readFromString(lineParts[1]);
			String conceptId = lineParts[0];
			adrUMLSConceptsVectors.put(v, conceptId);
		}
		buffer.close();
		vArr = new Vector[adrUMLSConceptsVectors.size()];
		vArr = adrUMLSConceptsVectors.keySet().toArray(vArr );
	}
	public String getMostSimilartConcepts(String phrase, Double minSimilarity) {
		phrase = phrase.replaceAll("[^0-9a-zA-Z]+", " ");
		Vector phraseVec = pubmedSystematicReviews.getPhraseVector(phrase, "svd_termvectors.bin", "svd_docvectors.bin", null);
		int i = VectorUtils.getNearestVector(phraseVec, vArr);
		Vector nearestV = vArr[i];
		String conceptId = adrUMLSConceptsVectors.get(nearestV);
		System.out.println(phrase +" --> "+conceptId);
		return conceptId;
	}
	
	static class VectorSim implements Comparable<VectorSim>{
		public Vector vector;
		public double similarity;
		public int compareTo(VectorSim vSim) {
			return (int)(1000*similarity) - (int)(1000*vSim.similarity);
		}
	}
	
	public List<String> getNMostSimilartConcepts(String phrase, Double minTreshold, int n) {
		phrase = phrase.replaceAll("[^0-9a-zA-Z]+", " ");
		Vector phraseVec = pubmedSystematicReviews.getPhraseVector(phrase, "svd_termvectors.bin", "svd_docvectors.bin", null);
		
		List<String> nearest = new ArrayList<String>();
		PriorityQueue<VectorSim> topNearestVecs = new PriorityQueue<ConceptSemanticSimilarity.VectorSim>(n);
	    for (int i = 0; i < vArr.length; ++i) {
	      double thisDist = phraseVec.measureOverlap(vArr[i]);
	      VectorSim vSim = new VectorSim();
	      vSim.similarity = thisDist;
	      vSim.vector = vArr[i];
		 topNearestVecs.add(vSim );
	      if (topNearestVecs.size() > n) {
	    	  topNearestVecs.poll();
	      }
	    }
	    for(VectorSim vSim : topNearestVecs){
			String conceptId = adrUMLSConceptsVectors.get(vSim.vector);
			nearest.add(conceptId);
	    }
		return nearest;
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
				if(adrConceptsSet.contains(conceptId)){
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


}
