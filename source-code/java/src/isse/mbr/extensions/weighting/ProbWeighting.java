package isse.mbr.extensions.weighting;

import java.util.StringTokenizer;

import isse.mbr.extensions.ExternalMorphism;
import isse.mbr.model.parsetree.PVSInstance;

/**
 * Calculates integer weights based on a specification
 * of a probabilistic CSP using log
 * 
 * - We have p(c holds) for every soft constraint c and need
 *   to convert this to appropriate weights w
 * @author Alexander Schiendorfer
 *
 */
public class ProbWeighting extends ExternalMorphism {

	public final static int PRECISION = 100; // digits

	
	@Override
	public void process(PVSInstance fromInstance) {
		String probs = fromInstance.getGeneratedCodeParameters().get("probs");
		int nScs = fromInstance.getNumberSoftConstraints();
		processMiniZincString(probs, nScs);
	}

	private void processMiniZincString(String weights, int nScs) {
		String processed = weights.replaceAll("\\[", "");
		processed = processed.replaceAll("\\]", "").trim();
		
		StringTokenizer tok = new StringTokenizer(processed, ",");
		long[] intWeights = new long[nScs];
		int index = -1;
		
		while(tok.hasMoreTokens()) {
			String nextProbStr = tok.nextToken().trim();
			double nextProb = Double.parseDouble(nextProbStr);
			double weight = -  Math.log(1.0 - nextProb);
			long intWeight = Math.round(weight*PRECISION);
			
			System.out.println("NextProb: "+nextProb + " leads to weight "+intWeight);
			intWeights[++index] = intWeight;
		}
		
		StringBuilder weightBuilder = new StringBuilder("[");
		
		for(int i = 1; i <= nScs; ++i){
			if(i != 1)
				weightBuilder.append(", ");
			weightBuilder.append(intWeights[i-1]);
		}
		weightBuilder.append("]");
		calculatedParameters.put("weights", weightBuilder.toString());
		
	}

}
