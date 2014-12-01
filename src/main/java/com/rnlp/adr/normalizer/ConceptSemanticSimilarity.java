package com.rnlp.adr.normalizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.AbstractMap.SimpleEntry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import octopus.semantic.similarity.Configuration;
import octopus.semantic.similarity.msr.IMSR;
import octopus.semantic.similarity.msr.vector.MSRLSA;
import octopus.semantic.similarity.resource.IMSRResource;
import octopus.semantic.similarity.resource.IMSRResource.ResourceType;
import octopus.semantic.similarity.resource.TextualCorpusResource.CorpusFormat;
import octopus.semantic.similarity.resource.TextualCorpusResource;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorUtils;
import rainbownlp.core.Phrase;
import rainbownlp.util.FileUtil;

public class ConceptSemanticSimilarity {
	IMSR lsaMSR;
	TextualCorpusResource pubmedSystematicReviews;
	static HashMap<String, List<String>> adrUMLSConceptsDefinition = new HashMap<String, List<String>>();
//	static HashMap<String, List<Vector>> adrUMLSConceptsVectors = new HashMap<String, List<Vector>>();
	static HashMap<Vector, String> adrUMLSConceptsVectors = new HashMap<Vector, String>();
	private static final String UMLS_DEF_FILE_PATH = "/media/NewHard_/corpora/umls/mmsys/2014AB/META/MRCONSO.RRF.aa";
	private static final String ADR_CONCEPTS_FILE_PATH = "data/conceptNames.tsv";
	private static final String ADR_CONCEPTS_FILE_PATH_DEF = "data/conceptNames_onlydirectused.def";
	static Set<String> adrConceptsSet = new HashSet<String>();
	static Vector[] vArr;
	
	public static void main(String[] args) throws Exception{
		List<String> adrConcepts = FileUtil.loadLineByLine(ADR_CONCEPTS_FILE_PATH);
		for(String adrCLine : adrConcepts){
			adrConceptsSet.add(adrCLine.split("\t")[0].toLowerCase());
		}
		createDefinitions();
	}
	public ConceptSemanticSimilarity() throws Exception{
		lsaMSR = new MSRLSA();
		pubmedSystematicReviews = new TextualCorpusResource("PubmedSystematicReviews",
				"/media/NewHard_/corpora/pubmed_result_systematic_reviews.txt", CorpusFormat.PUBMED_ABSTRACTS);
		
		loadDefinitions();
		
		for(String cId : adrUMLSConceptsDefinition.keySet()){
//			List<Vector> vectors = new ArrayList<Vector>();
			for(String def : adrUMLSConceptsDefinition.get(cId)){
				def = def.replaceAll("[^0-9a-zA-Z]+", " ");
				Vector v = pubmedSystematicReviews.getPhraseVector(def, "svd_termvectors.bin", "svd_docvectors.bin", null);
//				vectors.add(v);
				adrUMLSConceptsVectors.put(v, cId);
			}
		}
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
