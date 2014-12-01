package com.rnlp.adr.normalizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import octopus.semantic.similarity.HybridBAMSR;
import pitt.search.semanticvectors.vectors.Vector;
import rainbownlp.analyzer.evaluation.classification.Evaluator;
import rainbownlp.core.Phrase;
import rainbownlp.machinelearning.MLExample;
import rainbownlp.util.FileUtil;
import rainbownlp.util.HibernateUtil;

public class ADRNormalizer {
	private static final Double MIN_SIMILARITY = 0.0;
	static HashMap<String, List<String>> conceptVariations = new HashMap<String,List<String>>();
	static HashMap<String, String> conceptNameToId = new HashMap<String,String>();
	static Set<String> annotatedConcepts = new HashSet<String>();
	
	public static void main(String[] args) throws Exception{
		HibernateUtil.initialize("hibernate-oss.cfg.xml");
		loadConcepts(args[0]);
		List<MLExample> trainExamples = loadTrainingSet(args[1]);
//		System.out.println(annotatedConcepts);
//		System.out.println(annotatedConcepts.size());
		predictConcepts(trainExamples);
		Evaluator.getEvaluationResult(trainExamples).printResult();
	}
	
	private static void predictConcepts(List<MLExample> trainExamples) throws Exception {
		UMLSManager umlManager = new UMLSManager();
		ConceptSemanticSimilarity conceptSim = new ConceptSemanticSimilarity();
		for(MLExample ex : trainExamples){
			String phraseContent = ex.getRelatedPhrase().getPhraseContent();
			//exact match
			String conceptId = conceptNameToId.get(phraseContent);
			
			//remove duplicate characters match
			if(conceptId==null){
				String normalizedContent = normalizePhrase(phraseContent);
				conceptId = conceptNameToId.get(normalizedContent);
			}
			
//			if(conceptId==null){
//				List<String> combinations = getWordsCombinations(phraseContent.split(" "));
//				for(String comb: combinations){
//					conceptId = conceptNameToId.get(comb);
//					if(conceptId!=null) break;
//				}
//				
//			}
			
			//semantic match
			if(conceptId==null){
				conceptId = conceptSim.getMostSimilartConcepts(phraseContent, MIN_SIMILARITY);
//				
//				List<String> parents = new ArrayList<String>();
//				HybridBAMSR bamsr = new HybridBAMSR();
//				bamsr.modelName = "testmodel_UMNSRS_REL";
//				bamsr.modelFile = "/tmp/SVMMultiClass-train-testmodel_UMNSRS_REL-1-2-3-4-5-6-7-8-9.model";
//				conceptId = umlManager.getMostSimilarConcept(ex.getRelatedPhrase(), bamsr, null, parents );
//				System.out.println("Parents: "+parents);
			}
			
			ex.setPredictedClass(conceptId);
		}
	}
	
//	private static List<String> getWordsCombinations(List<String> combs, String[] words) {
//		List<String> combs = new ArrayList<String>();
//		for()
//		return combs;
//	}

	private static String normalizePhrase(String phraseContent) {
		StringBuilder normalized = new StringBuilder();
		char pre = ' ';
		for(int i=0;i<phraseContent.length();i++){
			if(phraseContent.charAt(i)!=pre){
				pre = phraseContent.charAt(i);
				normalized.append(pre);
			}
		}
		return normalized.toString();
	}
	private static List<MLExample> loadTrainingSet(String trainFile) {
		List<String> annotations = FileUtil.loadLineByLine(trainFile);
		List<MLExample> examples = new ArrayList<MLExample>();
		for(String annotation : annotations){
			String[] parts = annotation.split("\t");
			String type = parts[3];
			String conceptID = parts[4];
//			annotatedConcepts.add(conceptID);
			String text = parts[5];
			MLExample example = new MLExample();
			example.setExpectedClass(conceptID);
			Phrase p = Phrase.createIndependentPhrase(text.toLowerCase());
			example.setRelatedPhrase(p);
			examples.add(example);
		}
		return examples;
	}
	private static void loadConcepts(String conceptsFile) throws IOException {
		List<String> concepts = FileUtil.loadLineByLine(conceptsFile);
		for(String line : concepts){
			String[] parts = line.split("\t");
			String phrase =parts[1].toLowerCase();
			List<String> variations = conceptVariations.get(parts[0]);
			if(variations==null){
				variations = new ArrayList<String>();
			}
			variations.add(phrase);
			conceptVariations.put(parts[0], variations);
			conceptNameToId.put(phrase, parts[0]);
		}
	}
}

