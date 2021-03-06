package isse.mbr.integration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import isse.mbr.parsing.CodeGenerator;
import isse.mbr.parsing.MiniBrassCompiler;
import isse.mbr.parsing.MiniBrassParseException;
import isse.mbr.tools.BasicTestListener;
import isse.mbr.tools.MiniZincLauncher;

@RunWith(Parameterized.class)
public class SoftGlobalsTest {
	String minibrassModel = "test-models/testSoftGlobals.mbr";
	String minibrassCompiled = "test-models/testSoftGlobals_o.mzn";
	String minizincModel = "test-models/testSoftGlobals.mzn";
	private MiniBrassCompiler compiler;
	private MiniZincLauncher launcher;
	
	// parameterized test stuff
	@Parameters
	public static Collection<Object[]> data(){
		return Arrays.asList(new Object[][] {
				{"jacop", "fzn-jacop", "2"},
				{"gecode", "fzn-gecode", "2"},
				{"g12_fd", "flatzinc", "2"},
			//  Chuffed does not properly support the soft global decomposition
//				{"chuffed", "fzn-chuffed", "2"}
		});
	}

	private String mznGlobals, fznExec, expected;

	public SoftGlobalsTest(String a, String b, String expected){
		this.mznGlobals=a; this.fznExec=b; this.expected=expected;
	}
	
	@Before
	public void setUp() throws Exception {
		compiler = new MiniBrassCompiler(true);
		launcher = new MiniZincLauncher();
		launcher.setMinizincGlobals(mznGlobals);
		launcher.setFlatzincExecutable(fznExec);
	} 

	@Test
	public void testSoftGlobals() throws IOException, MiniBrassParseException {
		// 1. compile minibrass file
		File output = new File(minibrassCompiled);
		compiler.compile(new File(minibrassModel), output);
		Assert.assertTrue(output.exists());
		
		// 2. execute minisearch
		
		BasicTestListener listener = new BasicTestListener();
		launcher.addMiniZincResultListener(listener);
		launcher.runMiniSearchModel(new File(minizincModel), null, 60);
		
		// 3. check solution
		Assert.assertTrue(listener.isSolved());
		Assert.assertTrue(listener.isOptimal());
		
		// for the objective, we need to find out the variable name 
		// instance was "cr1"
		String obj = CodeGenerator.encodeString("overall","cfn1");
		// we expect a violation (in weights) of 1
		Assert.assertEquals(expected, listener.getObjectives().get(obj));
	}

}
