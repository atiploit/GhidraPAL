package ghidra.pal.wbc;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import ghidra.pal.util.Pair;
import ghidra.pal.util.Printer;
import ghidra.pal.wbc.PABundle;

abstract public class PowerAnalysis<B extends PABundle, P>  {
	protected int highest_period[];
	protected double highest_correlations[][];
	public final int nKeys; 
	public final int nBits; 
	public PowerAnalysis(int numKeys, int numBits) {
		nKeys = numKeys;
		nBits = numBits;
	}
	
	protected Function<CryptoBitVector, B> fnBundle;
	public void setBundleFn(Function<CryptoBitVector, B> f) {
		fnBundle = f;
	}
	
	abstract protected Iterable<Pair<Integer,Integer>> createGuess(P text, int sK);
	
	@SuppressWarnings("unchecked")
	protected B[][] generateGuesses(List<P> texts) {
		int nTraces = texts.size();
		B[][] guesses = (B[][])new PABundle[nKeys][];
		
		// Iterate through all 2^6 subkeys
		for(int sK = 0; sK < nKeys; sK++) {
			// Map the plaintexts to the guesses for the current subkey
			final int k = sK;
			Iterable<Pair<Integer,Integer>>[] subkeyGuesses = texts.stream().map((x) -> createGuess(x,k)).toArray(Iterable[]::new);

			// Create nBits CPABundle objects, one per bit of output
			CryptoBitVector[] bitLevel = IntStream.range(0,nBits).mapToObj((x) -> new CryptoBitVector(nTraces)).toArray(CryptoBitVector[]::new);
			
			// For each plaintext => SBOX output guess
			for(int g = 0; g < subkeyGuesses.length; g++) {
				Iterator<Pair<Integer,Integer>> bitIt = subkeyGuesses[g].iterator();
				while(bitIt.hasNext()) {
					Pair<Integer,Integer> n = bitIt.next();
					bitLevel[n.x].assignBit(g, n.y==1);
				}
			}
			// So after this, bitLevel[32] contains CryptoBitVectors of the size of
			// the number of traces. The contents of the bitvectors are the raw bits
			// from the guesses.
			
			// Store that information into the guesses array
			guesses[sK] = (B[])Arrays.stream(bitLevel).map(fnBundle).toArray(PABundle[]::new);
		}
		return guesses;
	}
	protected void preAnalysis() {
		// Allocate array for highest correlations per bit
		highest_period = new int[nBits];
		
		// Allocate array for highest correlations per bit, per key
		highest_correlations = new double[nBits][nKeys];
	}

	protected void recordMax(double cij, int nBit, int sK) {
		double aij = Math.abs(cij);
		double ahc = Math.abs(highest_correlations[nBit][sK]);
		if(ahc < aij) {
			highest_correlations[nBit][sK] = cij;
			//Printer.printf("New best correlation for bit %d, key %d: %f (previous was %f)\n", nBit, sK, cij, ahc);
		}
		double ahp = Math.abs(highest_correlations[nBit][highest_period[nBit]]);
		if(ahp < aij) {
			highest_period[nBit] = sK;
			//Printer.printf("New highest key for bit %d: %d (%f)\n", nBit, sK, aij);
		}		
	}
	
	public void analyzeTrace(List<CryptoBitVector> points, List<P> plaintexts) {
		B[][] guesses = generateGuesses(plaintexts);
		preAnalysis();

		// Iterate through all trace points, each of which being a CryptoBitVector
		// of the size of the number of traces.
		for(CryptoBitVector point : points) {
			// Create a CPABundle, which precomputes HW/denominator
			B pointBundle = fnBundle.apply(point);
			
			// Iterate through all bits
			for(int nBit = 0; nBit < nBits; nBit++) {
				
				// Iterate through all subkeys
				for(int sK = 0; sK < nKeys; sK++)
					recordMax(pointBundle.compute(guesses[sK][nBit]), nBit, sK);
			}
		}
		postAnalysisCommon();
		postAnalysisSpecific();
	}
	protected void postAnalysisCommon() {
		for(int i = 0; i < nBits; i++) {
			int hp = highest_period[i];
			Printer.printf("Best correlation for bit %02d: subkey %02x %f\n", i, hp, highest_correlations[i][hp]);
		}
	}
	protected void postAnalysisSpecific() {}
}