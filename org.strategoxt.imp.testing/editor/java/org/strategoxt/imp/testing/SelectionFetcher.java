package org.strategoxt.imp.testing;

import static org.spoofax.jsglr.client.imploder.AbstractTokenizer.getTokenAfter;
import static org.spoofax.jsglr.client.imploder.AbstractTokenizer.getTokenBefore;
import static org.spoofax.jsglr.client.imploder.IToken.TK_ESCAPE_OPERATOR;
import static org.spoofax.jsglr.client.imploder.ImploderAttachment.getLeftToken;
import static org.spoofax.jsglr.client.imploder.ImploderAttachment.getRightToken;

import java.util.ArrayList;
import java.util.List;

import org.spoofax.interpreter.terms.IStrategoList;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.jsglr.client.imploder.IToken;
import org.spoofax.terms.TermVisitor;
import org.strategoxt.imp.runtime.Environment;
import org.strategoxt.imp.runtime.stratego.StrategoTermPath;

/**
 * Lamely-named class for fetching selections in test
 * fragment (e.g., foo in [[ module [[foo]] ]]). 
 * 
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class SelectionFetcher {
	
	public IStrategoList fetch(IStrategoTerm parsedFragment) {
		final List<IStrategoTerm> results = new ArrayList<IStrategoTerm>();
		new TermVisitor() {
			IStrategoTerm unclosedChild;
			public void preVisit(IStrategoTerm term) {
				IToken left = getTokenBefore(getLeftToken(term));
				IToken right = getTokenAfter(getRightToken(term));
				if (isOpenQuote(left)) {
					if (isCloseQuote(right)) {
						results.add(term);
					} else {
						unclosedChild = term;
					}
				}
			}
			
			@Override
			public void postVisit(IStrategoTerm term) {
				IToken right = getTokenAfter(getRightToken(term));
				if (isCloseQuote(right)) {
					if (unclosedChild != null)
						results.add(StrategoTermPath.findCommonAncestor(unclosedChild, term));
					unclosedChild = null;
				}
			}
		}.visit(parsedFragment);
		return Environment.getTermFactory().makeList(results);
	}

	protected boolean isOpenQuote(IToken left) {
		return left != null && left.getKind() == TK_ESCAPE_OPERATOR && isQuoteOpenText(left.toString());
	}

	protected boolean isCloseQuote(IToken right) {
		return right != null && right.getKind() == TK_ESCAPE_OPERATOR && !isQuoteOpenText(right.toString());
	}

	protected boolean isQuoteOpenText(String contents) {
		// HACK: inspect string contents to find out if it's an open or close quote
		if (contents.contains("[")) {
			return true;
		} else {
			assert contents.contains("]");
			return false;
		}
	}
}