package org.strategoxt.imp.testing.strategies;

import static org.spoofax.interpreter.core.Tools.asJavaString;

import org.eclipse.imp.language.LanguageRegistry;
import org.spoofax.interpreter.core.InterpreterException;
import org.spoofax.interpreter.library.IOAgent;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.interpreter.terms.ITermFactory;
import org.strategoxt.imp.runtime.Environment;
import org.strategoxt.imp.runtime.dynamicloading.BadDescriptorException;
import org.strategoxt.imp.runtime.dynamicloading.Descriptor;
import org.strategoxt.imp.runtime.services.StrategoObserver;
import org.strategoxt.imp.runtime.stratego.EditorIOAgent;
import org.strategoxt.lang.Context;
import org.strategoxt.lang.Strategy;

/**
 * Invoke a strategy in a stratego instance belonging to a language plugin.
 * 
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class plugin_strategy_evaluate_0_2 extends Strategy {

	public static plugin_strategy_evaluate_0_2 instance = new plugin_strategy_evaluate_0_2();

	/**
	 * @return Fail(trace) for strategy failure, Error(message) a string for errors, or Some(term) for success.
	 */
	@Override
	public IStrategoTerm invoke(Context context, IStrategoTerm current, IStrategoTerm languageName, IStrategoTerm strategy) {
		ITermFactory factory = context.getFactory();
		try {
			Descriptor descriptor = Environment.getDescriptor(LanguageRegistry.findLanguage(asJavaString(languageName)));
			if (descriptor == null) throw new BadDescriptorException("No language known with the name " + languageName);
	        StrategoObserver observer = descriptor.createService(StrategoObserver.class, null);
	        IOAgent ioAgent = context.getIOAgent();
	        if (ioAgent instanceof EditorIOAgent) {
	        	// Make the console visible to users
	        	((EditorIOAgent) ioAgent).getDescriptor().setDynamicallyLoaded(true);
	        }
			observer.getRuntime().setIOAgent(ioAgent);
			observer.getRuntime().setCurrent(current);
			if (observer.getRuntime().evaluate((IStrategoAppl) strategy, true)) {
				current = observer.getRuntime().current();
				current = factory.makeAppl(factory.makeConstructor("Some", 1), current);
				return current;
			} else {
				observer.reportRewritingFailed();
				return factory.makeAppl(factory.makeConstructor("Fail", 1),
						factory.makeString("rewriting failed\n" + context.getTraceString()));
			}
		} catch (BadDescriptorException e) {
			Environment.logException("Problem loading descriptor for testing", e);
			return factory.makeAppl(factory.makeConstructor("Error", 1),
					factory.makeString("Problem loading descriptor for testing: " + e.getLocalizedMessage()));
		} catch (InterpreterException e) {
			Environment.logWarning("Problem evaluating strategy for testing", e);
			return factory.makeAppl(factory.makeConstructor("Error", 1),
					factory.makeString(e.getLocalizedMessage()));
		} catch (RuntimeException e) {
			Environment.logException("Problem evaluating strategy for testing", e);
			return factory.makeAppl(factory.makeConstructor("Error", 1),
					factory.makeString(e.getClass().getName() + ": " + e.getLocalizedMessage() + " (see error log)"));
		}
	}

}