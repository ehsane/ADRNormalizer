package com.rnlp.adr.normalizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import octopus.semantic.similarity.HybridBAMSR;
import rainbownlp.analyzer.evaluation.classification.Evaluator;
import rainbownlp.core.Phrase;
import rainbownlp.core.PhraseLink;
import rainbownlp.machinelearning.MLExample;
import rainbownlp.util.FileUtil;
import rainbownlp.util.HibernateUtil;

public class ADRNormalizer {
	static HashMap<String, List<String>> conceptVariations = new HashMap<String,List<String>>();
	static HashMap<String, String> conceptNameToId = new HashMap<String,String>();
	
	
	public static void main(String[] args) throws Exception{
		HibernateUtil.initialize("hibernate-oss.cfg.xml");
		loadConcepts(args[0]);
		List<MLExample> trainExamples = loadTrainingSet(args[1]);
		predictConcepts(trainExamples);
		Evaluator.getEvaluationResult(trainExamples).printResult();
	}
	static HybridBAMSR bamsr = new HybridBAMSR();
	
	private static void predictConcepts(List<MLExample> trainExamples) throws Exception {
		UMLSManager umlManager = new UMLSManager();
		bamsr.modelName = "testmodel_UMNSRS_REL";
		bamsr.modelFile = "/tmp/SVMMultiClass-train-testmodel_UMNSRS_REL-1-2-3-4-5-6-7-8-9.model";
		for(MLExample ex : trainExamples){
			String phraseContent = ex.getRelatedPhrase().getPhraseContent();
			//exact match
			String conceptId = conceptNameToId.get(phraseContent);
			
			//remove duplicate characters match
			if(conceptId==null){
				String normalizedContent = normalizePhrase(phraseContent);
				conceptId = conceptNameToId.get(normalizedContent);
			}
			
			//semantic match
			if(conceptId==null){
				conceptId = umlManager.getMostSimilarConcept(ex.getRelatedPhrase(), bamsr, null, null);
			}else
				ex.setPredictedClass(conceptId);
			
		}
	}
	
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

