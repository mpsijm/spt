package org.strategoxt.imp.testing;

import static org.spoofax.interpreter.core.Tools.isTermAppl;
import static org.spoofax.jsglr.client.imploder.ImploderAttachment.getLeftToken;
import static org.spoofax.jsglr.client.imploder.ImploderAttachment.getRightToken;
import static org.spoofax.jsglr.client.imploder.ImploderAttachment.getTokenizer;
import static org.spoofax.terms.Term.termAt;
import static org.spoofax.terms.Term.tryGetConstructor;
import static org.spoofax.terms.attachments.ParentAttachment.getParent;

import java.io.IOException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.imp.model.ISourceProject;
import org.eclipse.imp.parser.IParseController;
import org.spoofax.interpreter.terms.IStrategoAppl;
import org.spoofax.interpreter.terms.IStrategoConstructor;
import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.jsglr.client.imploder.IToken;
import org.spoofax.jsglr.client.imploder.ITokenizer;
import org.spoofax.jsglr.client.imploder.Token;
import org.spoofax.jsglr.shared.BadTokenException;
import org.spoofax.jsglr.shared.SGLRException;
import org.spoofax.jsglr.shared.TokenExpectedException;
import org.spoofax.terms.StrategoListIterator;
import org.strategoxt.imp.runtime.Environment;
import org.strategoxt.imp.runtime.dynamicloading.BadDescriptorException;
import org.strategoxt.imp.runtime.dynamicloading.Descriptor;
import org.strategoxt.imp.runtime.dynamicloading.DynamicParseController;
import org.strategoxt.imp.runtime.parser.JSGLRI;
import org.strategoxt.imp.runtime.parser.SGLRParseController;
import org.strategoxt.imp.runtime.stratego.SourceAttachment;
import org.strategoxt.lang.WeakValueHashMap;
import static org.spoofax.jsglr.client.imploder.ImploderAttachment.*;

/** 
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class CachedFragmentParser {

	private static final int FRAGMENT_PARSE_TIMEOUT = 3000;
	
	private static final IStrategoConstructor FAILS_0 =
		Environment.getTermFactory().makeConstructor("Fails", 0);
	
	private static final IStrategoConstructor FAILS_PARSING_0 =
		Environment.getTermFactory().makeConstructor("FailsParsing", 0);
	
	private final WeakValueHashMap<String, IStrategoTerm> failParseCache =
		new WeakValueHashMap<String, IStrategoTerm>();
	
	private final WeakValueHashMap<String, IStrategoTerm> successParseCache =
		new WeakValueHashMap<String, IStrategoTerm>();
	
	private Descriptor parseCacheDescriptor;
	
	private JSGLRI parser;
	
	private boolean isLastSyntaxCorrect;

	public void configure(Descriptor descriptor, IPath path, ISourceProject project) {
		if (parseCacheDescriptor != descriptor) {
			parseCacheDescriptor = descriptor;
			this.parser = getParser(descriptor, path, project);
			failParseCache.clear();
			successParseCache.clear();
		}
	}
	
	public boolean isInitialized() {
		return parser != null;
	}

	private JSGLRI getParser(Descriptor descriptor, IPath path, ISourceProject project) {
		try {
			if (descriptor == null) return null;
			
			IParseController controller;
			controller = descriptor.createService(IParseController.class, null);
			if (controller instanceof DynamicParseController)
				controller = ((DynamicParseController) controller).getWrapped();
			if (controller instanceof SGLRParseController) {
				SGLRParseController sglrController = (SGLRParseController) controller;
				controller.initialize(path, project, null);
				JSGLRI parser = sglrController.getParser(); 
				JSGLRI result = new JSGLRI(parser.getParseTable(), parser.getStartSymbol(), (SGLRParseController) controller);
				result.setTimeout(FRAGMENT_PARSE_TIMEOUT);
				result.setUseRecovery(true);
				return result;
			} else {
				throw new IllegalStateException("SGLRParseController expected");
			}
		} catch (BadDescriptorException e) {
			Environment.logWarning("Could not load parser for testing language");
		} catch (RuntimeException e) {
			Environment.logWarning("Could not load parser for testing language");
		}
		return null;
	}

	public IStrategoTerm parseCached(ITokenizer oldTokenizer, IStrategoTerm fragment)
			throws TokenExpectedException, BadTokenException, SGLRException, IOException {
		
		String fragmentInput = createTestFragmentString(oldTokenizer, fragment);
		boolean successExpected = isSuccessExpected(fragment);
		IStrategoTerm parsed = getCache(successExpected).get(fragmentInput);
		if (parsed != null) {
			isLastSyntaxCorrect = successExpected;
		} else {
			SGLRParseController controller = parser.getController();
			controller.getParseLock().lock();
			try {
				parsed = parser.parse(fragmentInput, oldTokenizer.getFilename());
			} finally {
				controller.getParseLock().unlock();
			}
			isLastSyntaxCorrect = getTokenizer(parsed).isSyntaxCorrect();
			IResource resource = controller.getResource();
			SourceAttachment.putSource(parsed, resource, controller);
			if (!successExpected)
				clearTokenErrors(getTokenizer(parsed));
			if (isLastSyntaxCorrect == successExpected)
				getCache(isLastSyntaxCorrect).put(fragmentInput, parsed);
		}
		return parsed;
	}

	private WeakValueHashMap<String, IStrategoTerm> getCache(
			boolean parseSuccess) {
		return parseSuccess ? successParseCache : failParseCache;
	}

	private String createTestFragmentString(ITokenizer tokenizer, IStrategoTerm term) {
		int fragmentOffset = getLeftToken(term).getStartOffset();
		IToken endToken = getRightToken(term);
		StringBuilder result = new StringBuilder(tokenizer.toString(tokenizer.getTokenAt(0), endToken));
		for (int i = 0; i < fragmentOffset; i++) {
			switch (result.charAt(i)) {
				case ' ': case '\t': case '\r': case '\n':
					break;
				default:
					result.setCharAt(i, ' ');
			}
		}
		return result.toString();
	}
	
	private boolean isSetupToken(IToken token) {
		if (token.getKind() != IToken.TK_STRING) return false;
		IStrategoTerm node = (IStrategoTerm) token.getAstNode();
		if (node != null && "Input".equals(getSort(node))) {
			IStrategoTerm parent = getParent(node);
			if (parent != null && isTermAppl(parent) && "Setup".equals(((IStrategoAppl) parent).getName()))
				return true;
		}
		return false;
	}
	
	private boolean isSuccessExpected(IStrategoTerm fragment) {
		IStrategoAppl test = (IStrategoAppl) getParent(getParent(fragment));
		if (test.getName().equals("Setup")) return true;
		IStrategoList expectations = termAt(test, test.getSubtermCount() - 1);
		for (IStrategoTerm expectation : StrategoListIterator.iterable(expectations)) {
			IStrategoConstructor cons = tryGetConstructor(expectation);
			if (cons == FAILS_0 || cons == FAILS_PARSING_0)
				return false;
		}
		return true;
	}
	
	public boolean isLastSyntaxCorrect() {
		return isLastSyntaxCorrect;
	}
	
	private void clearTokenErrors(ITokenizer tokenizer) {
		for (IToken token : tokenizer) {
			((Token) token).setError(null);
		}
	}
}
