/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mozilla.javascript.ast.AbstractObjectProperty;
import org.mozilla.javascript.ast.ArrayComprehension;
import org.mozilla.javascript.ast.ArrayComprehensionLoop;
import org.mozilla.javascript.ast.ArrayLiteral;
import org.mozilla.javascript.ast.Assignment;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Await;
import org.mozilla.javascript.ast.BigIntLiteral;
import org.mozilla.javascript.ast.Block;
import org.mozilla.javascript.ast.BreakStatement;
import org.mozilla.javascript.ast.CatchClause;
import org.mozilla.javascript.ast.ClassElement;
import org.mozilla.javascript.ast.ClassNode;
import org.mozilla.javascript.ast.Comment;
import org.mozilla.javascript.ast.ComputedPropertyKey;
import org.mozilla.javascript.ast.ConditionalExpression;
import org.mozilla.javascript.ast.ContinueStatement;
import org.mozilla.javascript.ast.DestructuringForm;
import org.mozilla.javascript.ast.DoLoop;
import org.mozilla.javascript.ast.ElementGet;
import org.mozilla.javascript.ast.EmptyExpression;
import org.mozilla.javascript.ast.EmptyStatement;
import org.mozilla.javascript.ast.ErrorNode;
import org.mozilla.javascript.ast.ExportDeclaration;
import org.mozilla.javascript.ast.ExportSpecifier;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.ForInLoop;
import org.mozilla.javascript.ast.ForLoop;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.GeneratorExpression;
import org.mozilla.javascript.ast.GeneratorExpressionLoop;
import org.mozilla.javascript.ast.GeneratorMethodDefinition;
import org.mozilla.javascript.ast.IdeErrorReporter;
import org.mozilla.javascript.ast.IfStatement;
import org.mozilla.javascript.ast.ImportDeclaration;
import org.mozilla.javascript.ast.ImportSpecifier;
import org.mozilla.javascript.ast.InfixExpression;
import org.mozilla.javascript.ast.Jump;
import org.mozilla.javascript.ast.KeywordLiteral;
import org.mozilla.javascript.ast.Label;
import org.mozilla.javascript.ast.LabeledStatement;
import org.mozilla.javascript.ast.LetNode;
import org.mozilla.javascript.ast.Loop;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NewExpression;
import org.mozilla.javascript.ast.NumberLiteral;
import org.mozilla.javascript.ast.ObjectLiteral;
import org.mozilla.javascript.ast.ObjectProperty;
import org.mozilla.javascript.ast.ParenthesizedExpression;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.RegExpLiteral;
import org.mozilla.javascript.ast.ReturnStatement;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.ScriptNode;
import org.mozilla.javascript.ast.Spread;
import org.mozilla.javascript.ast.SpreadObjectProperty;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.SwitchCase;
import org.mozilla.javascript.ast.SwitchStatement;
import org.mozilla.javascript.ast.Symbol;
import org.mozilla.javascript.ast.TaggedTemplateLiteral;
import org.mozilla.javascript.ast.TemplateCharacters;
import org.mozilla.javascript.ast.TemplateLiteral;
import org.mozilla.javascript.ast.ThrowStatement;
import org.mozilla.javascript.ast.TryStatement;
import org.mozilla.javascript.ast.UnaryExpression;
import org.mozilla.javascript.ast.UpdateExpression;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;
import org.mozilla.javascript.ast.WhileLoop;
import org.mozilla.javascript.ast.WithStatement;
import org.mozilla.javascript.ast.XmlDotQuery;
import org.mozilla.javascript.ast.XmlElemRef;
import org.mozilla.javascript.ast.XmlExpression;
import org.mozilla.javascript.ast.XmlLiteral;
import org.mozilla.javascript.ast.XmlMemberGet;
import org.mozilla.javascript.ast.XmlPropRef;
import org.mozilla.javascript.ast.XmlRef;
import org.mozilla.javascript.ast.XmlString;
import org.mozilla.javascript.ast.Yield;

/**
 * This class implements the JavaScript parser.
 *
 * <p>It is based on the SpiderMonkey C source files jsparse.c and jsparse.h in the jsref package.
 *
 * <p>The parser generates an {@link AstRoot} parse tree representing the source code. No tree
 * rewriting is permitted at this stage, so that the parse tree is a faithful representation of the
 * source for frontend processing tools and IDEs.
 *
 * <p>This parser implementation is not intended to be reused after a parse finishes, and will throw
 * an IllegalStateException() if invoked again.
 *
 * @see TokenStream
 * @author Mike McCabe
 * @author Brendan Eich
 */
public class Parser {
    /** Maximum number of allowed function or constructor arguments, to follow SpiderMonkey. */
    public static final int ARGC_LIMIT = 1 << 16;

    // TokenInformation flags : currentFlaggedToken stores them together
    // with token type
    static final int CLEAR_TI_MASK = 0xFFFF, // mask to clear token information bits
            TI_AFTER_EOL = 1 << 16, // first token of the source line
            TI_CHECK_LABEL = 1 << 17; // indicates to check for label

    CompilerEnvirons compilerEnv;
    private ErrorReporter errorReporter;
    private IdeErrorReporter errorCollector;
    private String sourceURI;
    private char[] sourceChars;

    boolean calledByCompileFunction; // ugly - set directly by Context
    private boolean parseFinished; // set when finished to prevent reuse

    private TokenStream ts;
    CurrentPositionReporter currentPos;
    private int currentFlaggedToken = Token.EOF;
    private int currentToken;
    private int syntaxErrorCount;

    private List<Comment> scannedComments;
    private Comment currentJsDocComment;

    protected int nestingOfFunction;
    protected int nestingOfFunctionParams;
    private LabeledStatement currentLabel;
    private boolean inDestructuringAssignment;
    // Flag to indicate we're inside a parenthesized expression that could become arrow parameters.
    // In this context, OBJECT_LITERAL_DESTRUCTURING check should be deferred to parenExpr.
    private boolean inPotentialArrowParams;
    protected boolean inUseStrictDirective;

    // Flag to indicate we're parsing an ES6 module (enables import/export)
    private boolean parsingModule;

    // Tracks nesting inside statement bodies (while, for, if, etc.)
    // Import/export are only allowed when this is 0 (at module top level)
    private int nestingOfStatement;

    // The following are per function variables and should be saved/restored
    // during function parsing.  See PerFunctionVariables class below.
    ScriptNode currentScriptOrFn;
    private boolean insideMethod;
    Scope currentScope;
    private int endFlags;
    private boolean inForInit; // bound temporarily during forStatement()
    // Tracks when we're parsing a single statement context (like if/while/for body without braces)
    // where lexical declarations are forbidden. In non-strict mode, 'let' followed by newline
    // should be treated as an identifier in such contexts.
    private boolean inSingleStatementContext;
    private Map<String, LabeledStatement> labelSet;
    private List<Loop> loopSet;
    private List<Jump> loopAndSwitchSet;
    private boolean hasUndefinedBeenRedefined = false;
    private boolean inAsyncFunction = false; // Track if we're inside an async function for await
    // end of per function variables

    // Lacking 2-token lookahead, labels become a problem.
    // These vars store the token info of the last matched name,
    // iff it wasn't the last matched token.
    private int prevNameTokenStart;
    private String prevNameTokenString = "";
    private int prevNameTokenLineno;
    private int prevNameTokenColumn;
    private boolean prevNameTokenContainsEscape;
    private int lastTokenLineno = -1;
    private int lastTokenColumn = -1;

    // Exception to unwind
    public static class ParserException extends RuntimeException {
        private static final long serialVersionUID = 5882582646773765630L;
    }

    static interface Transformer {
        Node transform(AstNode node);
    }

    public Parser() {
        this(new CompilerEnvirons());
    }

    public Parser(CompilerEnvirons compilerEnv) {
        this(compilerEnv, compilerEnv.getErrorReporter());
    }

    public Parser(CompilerEnvirons compilerEnv, ErrorReporter errorReporter) {
        this.compilerEnv = compilerEnv;
        this.errorReporter = errorReporter;
        if (errorReporter instanceof IdeErrorReporter) {
            errorCollector = (IdeErrorReporter) errorReporter;
        }
    }

    // Add a strict warning on the last matched token.
    void addStrictWarning(String messageId, String messageArg) {
        addStrictWarning(messageId, messageArg, currentPos.getPosition(), currentPos.getLength());
    }

    void addStrictWarning(String messageId, String messageArg, int position, int length) {
        if (compilerEnv.isStrictMode()) addWarning(messageId, messageArg, position, length);
    }

    void addWarning(String messageId, String messageArg) {
        addWarning(messageId, messageArg, currentPos.getPosition(), currentPos.getLength());
    }

    void addWarning(String messageId, int position, int length) {
        addWarning(messageId, null, position, length);
    }

    void addWarning(String messageId, String messageArg, int position, int length) {
        String message = lookupMessage(messageId, messageArg);
        if (compilerEnv.reportWarningAsError()) {
            addError(messageId, messageArg, position, length);
        } else if (errorCollector != null) {
            errorCollector.warning(message, sourceURI, position, length);
        } else {
            errorReporter.warning(
                    message,
                    sourceURI,
                    currentPos.getLineno(),
                    currentPos.getLine(),
                    currentPos.getOffset());
        }
    }

    void addError(String messageId) {
        addError(messageId, currentPos.getPosition(), currentPos.getLength());
    }

    void addError(String messageId, int position, int length) {
        addError(messageId, null, position, length);
    }

    void addError(String messageId, String messageArg) {
        addError(messageId, messageArg, currentPos.getPosition(), currentPos.getLength());
    }

    void addError(String messageId, int c) {
        String messageArg = Character.toString((char) c);
        addError(messageId, messageArg);
    }

    void addError(String messageId, String messageArg, int position, int length) {
        ++syntaxErrorCount;
        String message = lookupMessage(messageId, messageArg);
        if (errorCollector != null) {
            errorCollector.error(message, sourceURI, position, length);
        } else {
            errorReporter.error(
                    message,
                    sourceURI,
                    currentPos.getLineno(),
                    currentPos.getLine(),
                    currentPos.getOffset());
        }
    }

    private void addStrictWarning(
            String messageId,
            String messageArg,
            int position,
            int length,
            int line,
            String lineSource,
            int lineOffset) {
        if (compilerEnv.isStrictMode()) {
            addWarning(messageId, messageArg, position, length, line, lineSource, lineOffset);
        }
    }

    private void addWarning(
            String messageId,
            String messageArg,
            int position,
            int length,
            int line,
            String lineSource,
            int lineOffset) {
        String message = lookupMessage(messageId, messageArg);
        if (compilerEnv.reportWarningAsError()) {
            addError(messageId, messageArg, position, length, line, lineSource, lineOffset);
        } else if (errorCollector != null) {
            errorCollector.warning(message, sourceURI, position, length);
        } else {
            errorReporter.warning(message, sourceURI, line, lineSource, lineOffset);
        }
    }

    private void addError(
            String messageId,
            String messageArg,
            int position,
            int length,
            int line,
            String lineSource,
            int lineOffset) {
        ++syntaxErrorCount;
        String message = lookupMessage(messageId, messageArg);
        if (errorCollector != null) {
            errorCollector.error(message, sourceURI, position, length);
        } else {
            errorReporter.error(message, sourceURI, line, lineSource, lineOffset);
        }
    }

    String lookupMessage(String messageId) {
        return lookupMessage(messageId, null);
    }

    String lookupMessage(String messageId, String messageArg) {
        return messageArg == null
                ? ScriptRuntime.getMessageById(messageId)
                : ScriptRuntime.getMessageById(messageId, messageArg);
    }

    void reportError(String messageId) {
        reportError(messageId, null);
    }

    void reportError(String messageId, String messageArg) {
        reportError(messageId, messageArg, currentPos.getPosition(), currentPos.getLength());
    }

    void reportError(String messageId, int position, int length) {
        reportError(messageId, null, position, length);
    }

    void reportError(String messageId, String messageArg, int position, int length) {
        addError(messageId, messageArg, position, length);

        if (!compilerEnv.recoverFromErrors()) {
            throw new ParserException();
        }
    }

    // Computes the absolute end offset of node N.
    // Use with caution!  Assumes n.getPosition() is -absolute-, which
    // is only true before the node is added to its parent.
    private static int getNodeEnd(AstNode n) {
        return n.getPosition() + n.getLength();
    }

    private void recordComment(int lineno, int column, String comment) {
        if (scannedComments == null) {
            scannedComments = new ArrayList<>();
        }
        Comment commentNode =
                new Comment(ts.tokenBeg, ts.getTokenLength(), ts.commentType, comment);
        if (ts.commentType == Token.CommentType.JSDOC
                && compilerEnv.isRecordingLocalJsDocComments()) {
            currentJsDocComment =
                    new Comment(ts.tokenBeg, ts.getTokenLength(), ts.commentType, comment);
            currentJsDocComment.setLineColumnNumber(lineno, column);
        }
        commentNode.setLineColumnNumber(lineno, column);
        scannedComments.add(commentNode);
    }

    private Comment getAndResetJsDoc() {
        Comment saved = currentJsDocComment;
        currentJsDocComment = null;
        return saved;
    }

    // Returns the next token without consuming it.
    // If previous token was consumed, calls scanner to get new token.
    // If previous token was -not- consumed, returns it (idempotent).
    //
    // This function will not return a newline (Token.EOL - instead, it
    // gobbles newlines until it finds a non-newline token, and flags
    // that token as appearing just after a newline.
    //
    // This function will also not return a Token.COMMENT.  Instead, it
    // records comments in the scannedComments list.  If the token
    // returned by this function immediately follows a jsdoc comment,
    // the token is flagged as such.
    //
    // Note that this function always returned the un-flagged token!
    // The flags, if any, are saved in currentFlaggedToken.
    private int peekToken() throws IOException {
        // By far the most common case:  last token hasn't been consumed,
        // so return already-peeked token.
        if (currentFlaggedToken != Token.EOF) {
            return currentToken;
        }

        int tt = ts.getToken();
        boolean sawEOL = false;

        // process comments and whitespace
        while (tt == Token.EOL || tt == Token.COMMENT) {
            if (tt == Token.EOL) {
                sawEOL = true;
                tt = ts.getToken();
            } else {
                // Per ECMAScript spec, if a MultiLineComment contains a line terminator,
                // then the entire comment is considered to be a LineTerminator for ASI purposes.
                if (ts.getCommentContainsLineTerminator()) {
                    sawEOL = true;
                }
                if (compilerEnv.isRecordingComments()) {
                    String comment = ts.getAndResetCurrentComment();
                    recordComment(ts.getTokenStartLineno(), ts.getTokenColumn(), comment);
                    break;
                }
                tt = ts.getToken();
            }
        }

        currentToken = tt;
        currentFlaggedToken = tt | (sawEOL ? TI_AFTER_EOL : 0);
        return currentToken; // return unflagged token
    }

    private int lineNumber() {
        return lastTokenLineno;
    }

    private int columnNumber() {
        return lastTokenColumn;
    }

    private int peekFlaggedToken() throws IOException {
        peekToken();
        return currentFlaggedToken;
    }

    private void consumeToken() {
        currentFlaggedToken = Token.EOF;
        lastTokenLineno = ts.getTokenStartLineno();
        lastTokenColumn = ts.getTokenColumn();
    }

    private int nextToken() throws IOException {
        int tt = peekToken();
        consumeToken();
        return tt;
    }

    private boolean matchToken(int toMatch, boolean ignoreComment) throws IOException {
        int tt = peekToken();
        while (tt == Token.COMMENT && ignoreComment) {
            consumeToken();
            tt = peekToken();
        }
        if (tt != toMatch) {
            return false;
        }
        consumeToken();
        return true;
    }

    // Returns Token.EOL if the current token follows a newline, else returns
    // the current token.  Used in situations where we don't consider certain
    // token types valid if they are preceded by a newline.  One example is the
    // postfix ++ or -- operator, which has to be on the same line as its
    // operand.
    private int peekTokenOrEOL() throws IOException {
        int tt = peekToken();
        // Check for last peeked token flags
        if ((currentFlaggedToken & TI_AFTER_EOL) != 0) {
            tt = Token.EOL;
        }
        return tt;
    }

    private boolean mustMatchToken(int toMatch, String messageId, boolean ignoreComment)
            throws IOException {
        return mustMatchToken(
                toMatch, messageId, ts.tokenBeg, ts.tokenEnd - ts.tokenBeg, ignoreComment);
    }

    private boolean mustMatchToken(
            int toMatch, String msgId, int pos, int len, boolean ignoreComment) throws IOException {
        if (matchToken(toMatch, ignoreComment)) {
            return true;
        }
        reportError(msgId, pos, len);
        return false;
    }

    /**
     * Checks if the given token can be used as an identifier in the current context. In non-strict
     * mode, 'let' and 'yield' can be used as identifiers. In strict mode, they are reserved.
     * 'async' is always a contextual keyword and can be used as an identifier.
     */
    private boolean isIdentifierToken(int tt) {
        if (tt == Token.NAME) {
            return true;
        }
        // ES2017: 'async' is a contextual keyword, can always be used as identifier
        // (async function declarations are handled by lookahead in statementHelper)
        if (tt == Token.ASYNC && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
            return true;
        }
        // In ES6 non-strict mode, 'let' and 'yield' can be used as identifiers
        if (!inUseStrictDirective && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
            if (tt == Token.LET) {
                return true;
            }
            // Yield is only a keyword inside generators
            if (tt == Token.YIELD && !isCurrentFunctionGenerator()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Match an identifier token. In non-strict mode, this also accepts 'let' and 'yield' as
     * identifiers (when appropriate).
     */
    private boolean matchIdentifier(boolean ignoreComment) throws IOException {
        int tt = peekToken();
        while (tt == Token.COMMENT && ignoreComment) {
            consumeToken();
            tt = peekToken();
        }
        if (!isIdentifierToken(tt)) {
            return false;
        }
        consumeToken();
        return true;
    }

    /**
     * Require an identifier token. In non-strict mode, this also accepts 'let' and 'yield' as
     * identifiers (when appropriate).
     */
    private boolean mustMatchIdentifier(String messageId, boolean ignoreComment)
            throws IOException {
        if (matchIdentifier(ignoreComment)) {
            return true;
        }
        reportError(messageId, ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
        return false;
    }

    private void mustHaveXML() {
        if (!compilerEnv.isXmlAvailable()) {
            reportError("msg.XML.not.available");
        }
    }

    public boolean eof() {
        return ts.eof();
    }

    boolean insideFunctionBody() {
        return nestingOfFunction != 0;
    }

    boolean insideFunctionParams() {
        return nestingOfFunctionParams != 0;
    }

    void pushScope(Scope scope) {
        Scope parent = scope.getParentScope();
        // During codegen, parent scope chain may already be initialized,
        // in which case we just need to set currentScope variable.
        if (parent != null) {
            if (parent != currentScope) codeBug();
        } else {
            currentScope.addChildScope(scope);
        }
        currentScope = scope;
    }

    void popScope() {
        currentScope = currentScope.getParentScope();
    }

    private void enterLoop(Loop loop) {
        if (loopSet == null) loopSet = new ArrayList<>();
        loopSet.add(loop);
        if (loopAndSwitchSet == null) loopAndSwitchSet = new ArrayList<>();
        loopAndSwitchSet.add(loop);
        pushScope(loop);
        if (currentLabel != null) {
            currentLabel.setStatement(loop);
            currentLabel.getFirstLabel().setLoop(loop);
            // This is the only time during parsing that we set a node's parent
            // before parsing the children.  In order for the child node offsets
            // to be correct, we adjust the loop's reported position back to an
            // absolute source offset, and restore it when we call
            // restoreRelativeLoopPosition() (invoked just before setBody() is
            // called on the loop).
            loop.setRelative(-currentLabel.getPosition());
        }
    }

    private void exitLoop() {
        loopSet.remove(loopSet.size() - 1);
        loopAndSwitchSet.remove(loopAndSwitchSet.size() - 1);
        popScope();
    }

    private void restoreRelativeLoopPosition(Loop loop) {
        if (loop.getParent() != null) { // see comment in enterLoop
            loop.setRelative(loop.getParent().getPosition());
        }
    }

    private void enterSwitch(SwitchStatement node) {
        if (loopAndSwitchSet == null) loopAndSwitchSet = new ArrayList<>();
        loopAndSwitchSet.add(node);
    }

    private void exitSwitch() {
        loopAndSwitchSet.remove(loopAndSwitchSet.size() - 1);
    }

    /**
     * Builds a parse tree from the given source string.
     *
     * @return an {@link AstRoot} object representing the parsed program. If the parse fails, {@code
     *     null} will be returned. (The parse failure will result in a call to the {@link
     *     ErrorReporter} from {@link CompilerEnvirons}.)
     */
    public AstRoot parse(String sourceString, String sourceURI, int lineno) {
        if (parseFinished) throw new IllegalStateException("parser reused");
        this.sourceURI = sourceURI;
        if (compilerEnv.isIdeMode()) {
            this.sourceChars = sourceString.toCharArray();
        }
        currentPos = ts = new TokenStream(this, null, sourceString, lineno);
        try {
            return parse();
        } catch (IOException iox) {
            // Should never happen
            throw new IllegalStateException();
        } finally {
            parseFinished = true;
        }
    }

    /**
     * Builds a parse tree from the given source string, treating it as an ES6 module.
     *
     * <p>Module code is always in strict mode. Import and export declarations are allowed at the
     * top level.
     *
     * @param sourceString the source text to parse
     * @param sourceURI a string describing the source, such as a filename
     * @param lineno the starting line number
     * @return an {@link AstRoot} object representing the parsed module. If the parse fails, {@code
     *     null} will be returned.
     */
    public AstRoot parseModule(String sourceString, String sourceURI, int lineno) {
        if (parseFinished) throw new IllegalStateException("parser reused");
        this.sourceURI = sourceURI;
        if (compilerEnv.isIdeMode()) {
            this.sourceChars = sourceString.toCharArray();
        }
        this.parsingModule = true;
        currentPos = ts = new TokenStream(this, null, sourceString, lineno);
        try {
            return parse();
        } catch (IOException iox) {
            // Should never happen
            throw new IllegalStateException();
        } finally {
            parseFinished = true;
        }
    }

    /**
     * Returns true if we are currently parsing a module.
     *
     * @return true if parsing a module
     */
    public boolean isParsingModule() {
        return parsingModule;
    }

    /**
     * Builds a parse tree from the given sourcereader.
     *
     * @see #parse(String,String,int)
     * @throws IOException if the {@link Reader} encounters an error
     * @deprecated use parse(String, String, int) instead
     */
    @Deprecated
    public AstRoot parse(Reader sourceReader, String sourceURI, int lineno) throws IOException {
        if (parseFinished) throw new IllegalStateException("parser reused");
        if (compilerEnv.isIdeMode()) {
            return parse(Kit.readReader(sourceReader), sourceURI, lineno);
        }
        try {
            this.sourceURI = sourceURI;
            currentPos = ts = new TokenStream(this, sourceReader, null, lineno);
            return parse();
        } finally {
            parseFinished = true;
        }
    }

    private AstRoot parse() throws IOException {
        int pos = 0;
        AstRoot root = new AstRoot(pos);
        currentScope = currentScriptOrFn = root;

        int baseLineno = ts.lineno; // line number where source starts
        prevNameTokenLineno = ts.getLineno();
        prevNameTokenColumn = ts.getTokenColumn();
        int end = pos; // in case source is empty

        boolean inDirectivePrologue = true;
        boolean savedStrictMode = inUseStrictDirective;

        // Module code is always strict per ES6 spec
        inUseStrictDirective = compilerEnv.isStrictMode() || parsingModule;
        if (inUseStrictDirective) {
            root.setInStrictMode(true);
        }
        if (parsingModule) {
            root.setIsModule(true);
        }

        try {
            for (; ; ) {
                int tt = peekToken();
                if (tt <= Token.EOF) {
                    break;
                }

                AstNode n;
                if (tt == Token.FUNCTION) {
                    consumeToken();
                    try {
                        n =
                                function(
                                        calledByCompileFunction
                                                ? FunctionNode.FUNCTION_EXPRESSION
                                                : FunctionNode.FUNCTION_STATEMENT);
                    } catch (ParserException e) {
                        break;
                    }
                } else if (tt == Token.COMMENT) {
                    n = scannedComments.get(scannedComments.size() - 1);
                    consumeToken();
                } else {
                    n = statement();
                    if (inDirectivePrologue) {
                        String directive = getDirective(n);
                        if (directive == null) {
                            inDirectivePrologue = false;
                        } else if ("use strict".equals(directive)) {
                            inUseStrictDirective = true;
                            root.setInStrictMode(true);
                        }
                    }
                }
                end = getNodeEnd(n);
                root.addChildToBack(n);
                n.setParent(root);
            }
        } catch (StackOverflowError ex) {
            String msg = lookupMessage("msg.too.deep.parser.recursion");
            if (!compilerEnv.isIdeMode())
                throw Context.reportRuntimeError(msg, sourceURI, lineNumber(), null, 0);
        } finally {
            inUseStrictDirective = savedStrictMode;
        }

        reportErrorsIfExists(baseLineno);

        // add comments to root in lexical order
        if (scannedComments != null) {
            // If we find a comment beyond end of our last statement or
            // function, extend the root bounds to the end of that comment.
            int last = scannedComments.size() - 1;
            end = Math.max(end, getNodeEnd(scannedComments.get(last)));
            for (Comment c : scannedComments) {
                root.addComment(c);
            }
        }

        root.setLength(end - pos);
        root.setSourceName(sourceURI);
        root.setBaseLineno(baseLineno);
        root.setEndLineno(ts.getLineno());
        return root;
    }

    private AstNode parseFunctionBody(int type, FunctionNode fnNode) throws IOException {
        boolean isExpressionClosure = false;
        if (!matchToken(Token.LC, true)) {
            if (compilerEnv.getLanguageVersion() < Context.VERSION_1_8
                    && type != FunctionNode.ARROW_FUNCTION) {
                reportError("msg.no.brace.body");
            } else {
                isExpressionClosure = true;
            }
        }
        boolean isArrow = type == FunctionNode.ARROW_FUNCTION;
        ++nestingOfFunction;
        int pos = ts.tokenBeg;
        Block pn = new Block(pos); // starts at LC position

        // Function code that is supplied as the arguments to the built-in
        // Function, Generator, and AsyncFunction constructors is strict mode code
        // if the last argument is a String that when processed is a FunctionBody
        // that begins with a Directive Prologue that contains a Use Strict Directive.
        boolean inDirectivePrologue = true;
        boolean savedStrictMode = inUseStrictDirective;

        pn.setLineColumnNumber(lineNumber(), columnNumber());
        try {
            if (isExpressionClosure) {
                AstNode returnValue = assignExpr();
                ReturnStatement n =
                        new ReturnStatement(
                                returnValue.getPosition(), returnValue.getLength(), returnValue);
                // expression closure flag is required on both nodes
                n.putProp(Node.EXPRESSION_CLOSURE_PROP, Boolean.TRUE);
                n.setLineColumnNumber(returnValue.getLineno(), returnValue.getColumn());
                pn.putProp(Node.EXPRESSION_CLOSURE_PROP, Boolean.TRUE);
                if (isArrow) {
                    n.putProp(Node.ARROW_FUNCTION_PROP, Boolean.TRUE);
                }
                pn.addStatement(n);
                pn.setLength(n.getLength());
            } else {
                bodyLoop:
                for (; ; ) {
                    AstNode n;
                    int tt = peekToken();
                    switch (tt) {
                        case Token.ERROR:
                        case Token.EOF:
                        case Token.RC:
                            break bodyLoop;
                        case Token.COMMENT:
                            consumeToken();
                            n = scannedComments.get(scannedComments.size() - 1);
                            break;
                        case Token.FUNCTION:
                            consumeToken();
                            n = function(FunctionNode.FUNCTION_STATEMENT);
                            break;
                        case Token.CLASS:
                            consumeToken();
                            n = classDeclaration(ClassNode.CLASS_STATEMENT);
                            break;
                        default:
                            n = statement();
                            if (inDirectivePrologue) {
                                String directive = getDirective(n);
                                if (directive == null) {
                                    inDirectivePrologue = false;
                                } else if ("use strict".equals(directive)) {
                                    // ES6: strict mode function body with non-simple parameters is
                                    // an error
                                    boolean hasNonSimpleParams =
                                            fnNode.getDefaultParams() != null
                                                    || fnNode.hasRestParameter();

                                    // Check if params include destructuring patterns
                                    if (!hasNonSimpleParams) {
                                        for (AstNode param : fnNode.getParams()) {
                                            if (!(param instanceof Name)) {
                                                hasNonSimpleParams = true;
                                                break;
                                            }
                                        }
                                    }

                                    if (hasNonSimpleParams) {
                                        reportError("msg.default.args.use.strict");
                                    }

                                    // Check function name for strict mode violations
                                    String fnName = fnNode.getName();
                                    if (fnName != null
                                            && ("eval".equals(fnName)
                                                    || "arguments".equals(fnName))) {
                                        reportError("msg.bad.id.strict", fnName);
                                    }

                                    // Check parameter names for strict mode violations
                                    // now that we know the function body is strict
                                    Set<String> seenParams = new HashSet<>();
                                    for (AstNode param : fnNode.getParams()) {
                                        String paramName = null;
                                        if (param instanceof Name) {
                                            paramName = ((Name) param).getIdentifier();
                                        }
                                        if (paramName != null) {
                                            if ("eval".equals(paramName)
                                                    || "arguments".equals(paramName)) {
                                                reportError("msg.bad.id.strict", paramName);
                                            }
                                            if (seenParams.contains(paramName)) {
                                                addError("msg.dup.param.strict", paramName);
                                            }
                                            seenParams.add(paramName);
                                        }
                                    }
                                    inUseStrictDirective = true;
                                    fnNode.setInStrictMode(true);
                                    if (!savedStrictMode) {
                                        setRequiresActivation();
                                    }
                                }
                            }
                            break;
                    }
                    pn.addStatement(n);
                }
                int end = ts.tokenEnd;
                if (mustMatchToken(Token.RC, "msg.no.brace.after.body", true)) end = ts.tokenEnd;
                pn.setLength(end - pos);
            }
        } catch (ParserException e) {
            // Ignore it
        } finally {
            --nestingOfFunction;
            inUseStrictDirective = savedStrictMode;
        }

        getAndResetJsDoc();
        return pn;
    }

    private static String getDirective(AstNode n) {
        if (n instanceof ExpressionStatement) {
            AstNode e = ((ExpressionStatement) n).getExpression();
            if (e instanceof StringLiteral) {
                StringLiteral sl = (StringLiteral) e;
                // Per spec, directives must be string literals without escape sequences or line
                // continuations. If the string had escapes, it's not a valid directive.
                if (sl.hasEscapes()) {
                    return null;
                }
                return sl.getValue();
            }
        }
        return null;
    }

    private void parseFunctionParams(FunctionNode fnNode) throws IOException {
        ++nestingOfFunctionParams;
        try {
            if (matchToken(Token.RP, true)) {
                fnNode.setRp(ts.tokenBeg - fnNode.getPosition());
                return;
            }
            // Would prefer not to call createDestructuringAssignment until codegen,
            // but the symbol definitions have to happen now, before body is parsed.
            Map<String, Node> destructuring = null;
            Map<String, AstNode> destructuringDefault = null;

            Set<String> paramNames = new HashSet<>();
            do {
                int tt = peekToken();
                if (tt == Token.RP) {
                    if (fnNode.hasRestParameter()) {
                        // Error: parameter after rest parameter
                        reportError("msg.parm.after.rest", ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
                    }

                    fnNode.putIntProp(Node.TRAILING_COMMA, 1);
                    break;
                }
                if (tt == Token.LB || tt == Token.LC) {
                    if (fnNode.hasRestParameter()) {
                        // Error: parameter after rest parameter
                        reportError("msg.parm.after.rest", ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
                    }

                    AstNode expr = destructuringAssignExpr();
                    if (destructuring == null) {
                        destructuring = new HashMap<>();
                    }

                    if (expr instanceof Assignment) {
                        // We have default arguments inside destructured function parameters
                        // eg: f([x = 1] = [2]) { ... }, transform this into:
                        // f(x) {
                        //      if ($1 == undefined)
                        //          var $1 = [2];
                        //      if (x == undefined)
                        //          if (($1[0]) == undefined)
                        //              var x = 1;
                        //          else
                        //              var x = $1[0];
                        // }
                        // fnNode.addParam(name)
                        AstNode lhs = ((Assignment) expr).getLeft(); // [x = 1]
                        AstNode rhs = ((Assignment) expr).getRight(); // [2]
                        markDestructuring(lhs);
                        fnNode.addParam(lhs);
                        String pname = currentScriptOrFn.getNextTempName();
                        defineSymbol(Token.LP, pname, false);
                        if (destructuringDefault == null) {
                            destructuringDefault = new HashMap<>();
                        }
                        destructuring.put(pname, lhs);
                        destructuringDefault.put(pname, rhs);
                    } else {
                        markDestructuring(expr);
                        fnNode.addParam(expr);
                        // Destructuring assignment for parameters: add a dummy
                        // parameter name, and add a statement to the body to initialize
                        // variables from the destructuring assignment
                        String pname = currentScriptOrFn.getNextTempName();
                        defineSymbol(Token.LP, pname, false);
                        destructuring.put(pname, expr);
                    }
                } else {
                    boolean wasRest = false;
                    int restStartLineno = -1, restStartColumn = -1;
                    if (tt == Token.DOTDOTDOT) {
                        if (fnNode.hasRestParameter()) {
                            // Error: parameter after rest parameter
                            reportError(
                                    "msg.parm.after.rest", ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
                        }

                        fnNode.setHasRestParameter(true);
                        wasRest = true;
                        consumeToken();
                        restStartLineno = lineNumber();
                        restStartColumn = columnNumber();

                        // ES6: Rest parameter can be a destructuring pattern
                        // e.g., function f(...[a, b]) {} or function f(...{x, y}) {}
                        int nextTt = peekToken();
                        if (nextTt == Token.LB || nextTt == Token.LC) {
                            AstNode expr = destructuringAssignExpr();
                            if (destructuring == null) {
                                destructuring = new HashMap<>();
                            }
                            markDestructuring(expr);
                            fnNode.addParam(expr);
                            String pname = currentScriptOrFn.getNextTempName();
                            defineSymbol(Token.LP, pname, false);
                            destructuring.put(pname, expr);
                            continue; // Skip the regular parameter handling below
                        }
                    }

                    if (matchToken(Token.UNDEFINED, true)
                            || matchIdentifier(true)
                            || mustMatchIdentifier("msg.no.parm", true)) {

                        if (!wasRest && fnNode.hasRestParameter()) {
                            // Error: parameter after rest parameter
                            reportError(
                                    "msg.parm.after.rest", ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
                        }

                        Name paramNameNode = createNameNode();
                        if (wasRest) {
                            paramNameNode.setLineColumnNumber(restStartLineno, restStartColumn);
                        }
                        Comment jsdocNodeForName = getAndResetJsDoc();
                        if (jsdocNodeForName != null) {
                            paramNameNode.setJsDocNode(jsdocNodeForName);
                        }
                        fnNode.addParam(paramNameNode);
                        String paramName = ts.getString();
                        defineSymbol(Token.LP, paramName);
                        if (this.inUseStrictDirective) {
                            if ("eval".equals(paramName) || "arguments".equals(paramName)) {
                                reportError("msg.bad.id.strict", paramName);
                            }
                            if (paramNames.contains(paramName))
                                addError("msg.dup.param.strict", paramName);
                            paramNames.add(paramName);
                        }

                        if (matchToken(Token.ASSIGN, true)) {
                            if (wasRest) {
                                // ES6: Rest parameter cannot have an initializer
                                reportError("msg.no.rest.default");
                                // Still parse the expression to recover
                                assignExpr();
                            } else if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                                fnNode.putDefaultParams(paramName, assignExpr());
                            } else {
                                reportError("msg.default.args");
                            }
                        }
                    } else {
                        fnNode.addParam(makeErrorNode());
                    }
                }
            } while (matchToken(Token.COMMA, true));

            if (destructuring != null) {
                Node destructuringNode = new Node(Token.COMMA);
                // Add assignment helper for each destructuring parameter
                for (Map.Entry<String, Node> param : destructuring.entrySet()) {
                    AstNode defaultValue = null;
                    if (destructuringDefault != null) {
                        defaultValue = destructuringDefault.get(param.getKey());
                    }
                    Node assign =
                            createDestructuringAssignment(
                                    Token.VAR,
                                    param.getValue(),
                                    createName(param.getKey()),
                                    defaultValue);
                    destructuringNode.addChildToBack(assign);
                }
                fnNode.putProp(Node.DESTRUCTURING_PARAMS, destructuringNode);
            }

            if (mustMatchToken(Token.RP, "msg.no.paren.after.parms", true)) {
                fnNode.setRp(ts.tokenBeg - fnNode.getPosition());
            }

            // ES6 14.1.2: If FormalParameters contains non-simple parameters (defaults,
            // rest, or destructuring), duplicate parameter names are always an error,
            // even in non-strict mode.
            if (!inUseStrictDirective && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                boolean hasNonSimpleParams =
                        fnNode.getDefaultParams() != null
                                || fnNode.hasRestParameter()
                                || destructuring != null;

                if (hasNonSimpleParams) {
                    // Check for duplicates among all parameter names
                    Set<String> seen = new HashSet<>();
                    for (AstNode param : fnNode.getParams()) {
                        if (param instanceof Name) {
                            String pname = ((Name) param).getIdentifier();
                            if (seen.contains(pname)) {
                                addError("msg.dup.param.strict", pname);
                            }
                            seen.add(pname);
                        }
                    }
                }
            }
        } finally {
            --nestingOfFunctionParams;
        }
    }

    private FunctionNode function(int type) throws IOException {
        return function(type, false, false, false);
    }

    private FunctionNode function(int type, boolean isMethodDefiniton) throws IOException {
        return function(type, isMethodDefiniton, false, false);
    }

    private FunctionNode function(int type, boolean isMethodDefiniton, boolean isGeneratorMethod)
            throws IOException {
        return function(type, isMethodDefiniton, isGeneratorMethod, false);
    }

    private FunctionNode function(
            int type, boolean isMethodDefiniton, boolean isGeneratorMethod, boolean isAsync)
            throws IOException {
        boolean isGenerator = isGeneratorMethod;
        int syntheticType = type;
        int baseLineno = lineNumber(); // line number where source starts
        int functionSourceStart = ts.tokenBeg; // start of "function" kwd
        int functionStartColumn = columnNumber();
        Name name = null;
        AstNode memberExprNode = null;

        do {
            if (matchToken(Token.NAME, true) || matchToken(Token.UNDEFINED, true)) {
                name = createNameNode(true, Token.NAME);
                String id = name.getIdentifier();
                if (inUseStrictDirective) {
                    if ("eval".equals(id) || "arguments".equals(id)) {
                        reportError("msg.bad.id.strict", id);
                    }
                }
                // Generator functions cannot be named "yield" since yield is a keyword
                // inside generator function bodies.
                if (isGenerator
                        && "yield".equals(id)
                        && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    reportError("msg.reserved.id", id);
                }
                if (!matchToken(Token.LP, true)) {
                    if (compilerEnv.isAllowMemberExprAsFunctionName()) {
                        AstNode memberExprHead = name;
                        name = null;
                        memberExprNode = memberExprTail(false, memberExprHead);
                    }
                    mustMatchToken(Token.LP, "msg.no.paren.parms", true);
                }
            } else if (matchToken(Token.LP, true)) {
                // Anonymous function:  leave name as null
            } else if (matchToken(Token.MUL, true)
                    && (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6)) {
                // ES6 generator function
                isGenerator = true;
                continue;
            } else {
                if (compilerEnv.isAllowMemberExprAsFunctionName()) {
                    // Note that memberExpr can not start with '(' like
                    // in function (1+2).toString(), because 'function (' already
                    // processed as anonymous function
                    memberExprNode = memberExpr(false);
                }
                mustMatchToken(Token.LP, "msg.no.paren.parms", true);
            }
            break;
        } while (isGenerator);
        int lpPos = currentToken == Token.LP ? ts.tokenBeg : -1;

        if (memberExprNode != null) {
            syntheticType = FunctionNode.FUNCTION_EXPRESSION;
        }

        boolean skipAnnexBHoisting = false;
        if (syntheticType != FunctionNode.FUNCTION_EXPRESSION
                && name != null
                && name.length() > 0) {
            // Function statements define a symbol in the enclosing scope
            skipAnnexBHoisting =
                    defineSymbol(Token.FUNCTION, name.getIdentifier(), false, isGenerator);
        }

        FunctionNode fnNode = new FunctionNode(functionSourceStart, name);
        fnNode.setMethodDefinition(isMethodDefiniton);
        fnNode.setFunctionType(type);
        fnNode.setSkipAnnexBHoisting(skipAnnexBHoisting);
        if (isGenerator) {
            fnNode.setIsES6Generator();
        }
        if (isAsync) {
            fnNode.setIsAsync();
        }
        if (lpPos != -1) fnNode.setLp(lpPos - functionSourceStart);

        fnNode.setJsDocNode(getAndResetJsDoc());

        // If we're already in a strict mode context (e.g., class body), propagate to this function.
        // This ensures class methods are always in strict mode per ES6 14.5.
        if (inUseStrictDirective) {
            fnNode.setInStrictMode(true);
        }

        PerFunctionVariables savedVars = new PerFunctionVariables(fnNode);
        boolean wasInsideMethod = insideMethod;
        insideMethod = isMethodDefiniton;
        try {
            // Set async context after saving variables so await can be recognized
            if (isAsync) {
                inAsyncFunction = true;
            }
            parseFunctionParams(fnNode);
            AstNode body = parseFunctionBody(type, fnNode);
            fnNode.setBody(body);
            int end = functionSourceStart + body.getPosition() + body.getLength();
            fnNode.setRawSourceBounds(functionSourceStart, end);
            fnNode.setLength(end - functionSourceStart);

            if (compilerEnv.isStrictMode() && !fnNode.getBody().hasConsistentReturnUsage()) {
                String msg =
                        (name != null && name.length() > 0)
                                ? "msg.no.return.value"
                                : "msg.anon.no.return.value";
                addStrictWarning(msg, name == null ? "" : name.getIdentifier());
            }
        } finally {
            savedVars.restore();
            insideMethod = wasInsideMethod;
        }

        if (memberExprNode != null) {
            // TODO(stevey): fix missing functionality
            Kit.codeBug();
            fnNode.setMemberExprNode(memberExprNode); // rewrite later
            /* old code:
            if (memberExprNode != null) {
                pn = nf.createAssignment(Token.ASSIGN, memberExprNode, pn);
                if (functionType != FunctionNode.FUNCTION_EXPRESSION) {
                    // XXX check JScript behavior: should it be createExprStatement?
                    pn = nf.createExprStatementNoReturn(pn, baseLineno);
                }
            }
            */
        }

        fnNode.setSourceName(sourceURI);
        fnNode.setLineColumnNumber(baseLineno, functionStartColumn);
        fnNode.setEndLineno(lineNumber());

        // Set the parent scope.  Needed for finding undeclared vars.
        // Have to wait until after parsing the function to set its parent
        // scope, since defineSymbol needs the defining-scope check to stop
        // at the function boundary when checking for redeclarations.
        if (compilerEnv.isIdeMode()) {
            fnNode.setParentScope(currentScope);
        }
        return fnNode;
    }

    /**
     * Parses async function declaration or async expression. Called when Token.ASYNC is seen. The
     * async keyword is a contextual keyword - it only acts as a keyword when followed by function
     * (or an arrow function parameter list).
     */
    private AstNode asyncFunctionOrExpression() throws IOException {
        consumeToken(); // consume 'async'
        int asyncPos = ts.tokenBeg;
        int asyncLineno = lineNumber();
        int asyncColumn = columnNumber();
        boolean asyncContainsEscape = ts.identifierContainsEscape();

        // Check for line terminator between async and function (not allowed)
        int tt = peekTokenOrEOL();
        if (tt == Token.EOL) {
            // Line terminator after async - treat 'async' as identifier
            saveNameTokenData(asyncPos, "async", asyncLineno, asyncColumn, asyncContainsEscape);
            AstNode name = createNameNode(true, Token.NAME);
            AstNode pn = new ExpressionStatement(name, !insideFunctionBody());
            pn.setLineColumnNumber(asyncLineno, asyncColumn);
            return pn;
        }

        // If async was written with escapes, it's not the async keyword
        tt = peekToken();
        if (!asyncContainsEscape && tt == Token.FUNCTION) {
            consumeToken();
            // Check for async generator: async function*
            boolean isGenerator = false;
            if (matchToken(Token.MUL, true)) {
                isGenerator = true;
            }
            return function(FunctionNode.FUNCTION_EXPRESSION_STATEMENT, false, isGenerator, true);
        }

        // Not followed by 'function' - could be async arrow function or just identifier
        // Treat 'async' as identifier
        saveNameTokenData(asyncPos, "async", asyncLineno, asyncColumn, asyncContainsEscape);
        AstNode name = createNameNode(true, Token.NAME);
        AstNode pn = new ExpressionStatement(name, !insideFunctionBody());
        pn.setLineColumnNumber(asyncLineno, asyncColumn);
        return pn;
    }

    /**
     * Parses an ES6 class declaration or expression.
     *
     * <pre>
     * ClassDeclaration:
     *     class BindingIdentifier ClassTail
     * ClassExpression:
     *     class BindingIdentifier_opt ClassTail
     * ClassTail:
     *     ClassHeritage_opt { ClassBody_opt }
     * ClassHeritage:
     *     extends LeftHandSideExpression
     * </pre>
     *
     * @param classType CLASS_STATEMENT or CLASS_EXPRESSION
     * @return the ClassNode
     */
    private ClassNode classDeclaration(int classType) throws IOException {
        if (compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
            reportError("msg.class.not.available");
        }

        int classStart = ts.tokenBeg;
        int lineno = lineNumber();
        int column = columnNumber();

        Name className = null;
        AstNode superClass = null;

        // Parse optional class name
        if (matchToken(Token.NAME, true)) {
            className = createNameNode(true, Token.NAME);
            // Class definitions are always strict mode code per ES6 10.2.1,
            // so reserved words in strict mode cannot be used as class names.
            String id = className.getIdentifier();
            if ("eval".equals(id) || "arguments".equals(id)) {
                reportError("msg.bad.id.strict", id);
            } else if ("yield".equals(id)
                    && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                // yield is reserved in strict mode (which classes always are)
                reportError("msg.reserved.id", id);
            }
        } else if (classType == ClassNode.CLASS_STATEMENT) {
            // Class declarations require a name
            reportError("msg.class.name.expected");
        }

        // Define the class name in the enclosing scope for declarations
        if (classType == ClassNode.CLASS_STATEMENT && className != null) {
            defineSymbol(Token.LET, className.getIdentifier());
        }

        // Parse optional extends clause
        if (matchToken(Token.EXTENDS, true)) {
            superClass = memberExpr(false);
        }

        // Parse class body
        mustMatchToken(Token.LC, "msg.no.brace.class", true);
        int lcPos = ts.tokenBeg;

        ClassNode classNode = new ClassNode(classStart);
        classNode.setClassName(className);
        classNode.setSuperClass(superClass);
        classNode.setClassType(classType);
        classNode.setLc(lcPos - classStart);
        classNode.setLineColumnNumber(lineno, column);

        // Enter a new scope for the class body
        // Class body is implicitly strict mode
        boolean savedStrictMode = inUseStrictDirective;
        inUseStrictDirective = true;

        try {
            // Parse class elements
            boolean hasConstructor = false;
            while (peekToken() != Token.RC) {
                if (peekToken() == Token.SEMI) {
                    consumeToken();
                    continue;
                }
                ClassElement element = parseClassElement();
                if (element != null) {
                    // Check for duplicate constructor
                    if (!element.isStatic() && element.isConstructor()) {
                        if (hasConstructor) {
                            reportError("msg.class.duplicate.constructor");
                        }
                        hasConstructor = true;
                    }
                    classNode.addElement(element);
                }
            }
        } finally {
            inUseStrictDirective = savedStrictMode;
        }

        mustMatchToken(Token.RC, "msg.no.brace.class", true);
        classNode.setRc(ts.tokenBeg - classStart);
        classNode.setLength(ts.tokenEnd - classStart);

        return classNode;
    }

    /**
     * Parses a single class element (method definition).
     *
     * <pre>
     * ClassElement:
     *     MethodDefinition
     *     static MethodDefinition
     *     ;
     * </pre>
     */
    private ClassElement parseClassElement() throws IOException {
        int pos = ts.tokenBeg;
        boolean isStatic = false;
        boolean isGenerator = false;
        boolean isAsync = false;

        // Check for 'static' keyword
        if (matchToken(Token.STATIC, true)) {
            // Could be 'static' method or a method named 'static'
            int next = peekToken();
            if (next == Token.LP) {
                // Method named 'static' - method()
                return parseClassMethod(
                        pos, createNameNode(false, Token.NAME, "static"), false, false, false);
            } else if (next == Token.RC || next == Token.SEMI) {
                // Just 'static' followed by } or ; - treat as method named 'static'
                // This is actually a syntax error in real ES6, but we'll let it slide
                reportError("msg.class.unexpected.static");
                return null;
            }
            isStatic = true;
            pos = ts.tokenBeg;
        }

        // Check for async method (must come before generator check)
        // async keyword must not contain escape sequences
        if (matchToken(Token.ASYNC, true) && !ts.identifierContainsEscape()) {
            // Check if followed by line terminator (not allowed before method name)
            int nextTt = peekTokenOrEOL();
            if (nextTt == Token.EOL) {
                // Line terminator after async - treat 'async' as a field/method name
                return parseClassMethodOrField(
                        pos, createNameNode(false, Token.NAME, "async"), isStatic);
            }
            // Check if it's a method named 'async' (async followed by `(`)
            nextTt = peekToken();
            if (nextTt == Token.LP) {
                // Method named 'async' - async()
                return parseClassMethod(
                        pos, createNameNode(false, Token.NAME, "async"), isStatic, false, false);
            }
            // async generator method: async * name() {}
            if (matchToken(Token.MUL, true)) {
                isGenerator = true;
            }
            isAsync = true;
        } else if (matchToken(Token.MUL, true)) {
            // Check for generator method (only if not async)
            isGenerator = true;
        }

        // Check for getter/setter
        int entryKind = METHOD_ENTRY;
        int tt = peekToken();
        boolean isPrivate = false;

        if (tt == Token.NAME || tt == Token.STRING || tt == Token.NUMBER) {
            String tokenStr = ts.getString();
            // get/set not allowed with async
            if (!isGenerator && !isAsync && ("get".equals(tokenStr) || "set".equals(tokenStr))) {
                consumeToken();
                int nextTt = peekToken();
                if (nextTt != Token.LP) {
                    // It's a getter or setter
                    entryKind = "get".equals(tokenStr) ? GET_ENTRY : SET_ENTRY;
                    // Check if the getter/setter target is private
                    if (nextTt == Token.PRIVATE_NAME) {
                        isPrivate = true;
                    }
                } else {
                    // It's a method named 'get' or 'set'
                    return parseClassMethod(
                            pos,
                            createNameNode(false, Token.NAME, tokenStr),
                            isStatic,
                            false,
                            false);
                }
            }
        }

        // Check if this is a private member
        tt = peekToken();
        if (tt == Token.PRIVATE_NAME) {
            isPrivate = true;
        }

        // Parse property name
        AstNode propName = classPropertyName();
        if (propName == null) {
            reportError("msg.bad.prop");
            return null;
        }
        consumeToken();

        // Check if this is a field or a method
        // Fields: propName = value; or propName; or propName } (no parenthesis)
        // Methods: propName(params) { body }
        int nextToken = peekToken();
        if (!isGenerator && !isAsync && entryKind == METHOD_ENTRY && nextToken != Token.LP) {
            // This is a field definition, not a method
            return parseClassField(pos, propName, isStatic, isPrivate);
        }

        // Parse method
        return parseClassMethod(
                pos, propName, isStatic, isGenerator, isAsync, entryKind, isPrivate);
    }

    /**
     * Helper method to parse a class method or field when we've already seen a name. This is used
     * when we've consumed a token like 'async' but determined it's not a modifier.
     */
    private ClassElement parseClassMethodOrField(int pos, AstNode propName, boolean isStatic)
            throws IOException {
        int nextToken = peekToken();
        if (nextToken == Token.LP) {
            return parseClassMethod(pos, propName, isStatic, false, false);
        }
        return parseClassField(pos, propName, isStatic, false);
    }

    /**
     * Parses a class field definition.
     *
     * <pre>
     * FieldDefinition :
     *     ClassElementName Initializer_opt
     * </pre>
     */
    private ClassElement parseClassField(
            int pos, AstNode propName, boolean isStatic, boolean isPrivate) throws IOException {
        ClassElement element = new ClassElement(pos);
        element.setPropertyName(propName);
        element.setIsStatic(isStatic);
        element.setIsField(true);
        element.setIsComputed(propName instanceof ComputedPropertyKey);
        element.setIsPrivate(isPrivate);

        // Check for initializer
        if (matchToken(Token.ASSIGN, true)) {
            // Parse the initializer expression
            AstNode init = assignExpr();
            element.setInitializer(init);
        }

        // Consume the semicolon if present (ASI may handle it)
        matchToken(Token.SEMI, true);

        element.setLength(ts.tokenEnd - pos);
        return element;
    }

    private ClassElement parseClassMethod(
            int pos, AstNode propName, boolean isStatic, boolean isGenerator, boolean isAsync)
            throws IOException {
        return parseClassMethod(pos, propName, isStatic, isGenerator, isAsync, METHOD_ENTRY, false);
    }

    private ClassElement parseClassMethod(
            int pos,
            AstNode propName,
            boolean isStatic,
            boolean isGenerator,
            boolean isAsync,
            int entryKind)
            throws IOException {
        return parseClassMethod(pos, propName, isStatic, isGenerator, isAsync, entryKind, false);
    }

    private ClassElement parseClassMethod(
            int pos,
            AstNode propName,
            boolean isStatic,
            boolean isGenerator,
            boolean isAsync,
            int entryKind,
            boolean isPrivate)
            throws IOException {
        // Get the property name string for validation
        String propNameStr = null;
        if (propName instanceof Name) {
            propNameStr = ((Name) propName).getIdentifier();
        } else if (propName instanceof StringLiteral) {
            propNameStr = ((StringLiteral) propName).getValue();
        }

        // Check if this is a constructor (name is "constructor" and not static)
        boolean isConstructor = false;
        if (!isStatic && "constructor".equals(propNameStr)) {
            isConstructor = true;
            // Getters, setters, generators, and async methods cannot be named "constructor"
            if (entryKind == GET_ENTRY || entryKind == SET_ENTRY || isGenerator || isAsync) {
                reportError("msg.class.special.constructor");
            }
        }

        // Static public methods cannot be named "prototype"
        // (Private methods like static #prototype() are allowed)
        if (isStatic && !isPrivate && "prototype".equals(propNameStr)) {
            reportError("msg.class.static.prototype");
        }

        // Parse the function
        // All class methods (including constructors) need isMethodDefinition=true
        // during parsing so that `super` is allowed in the parser.
        // Pass isGenerator and isAsync so the function body handles yield/await correctly.
        FunctionNode fn = function(FunctionNode.FUNCTION_EXPRESSION, true, isGenerator, isAsync);
        if (isAsync) {
            fn.setIsAsync();
        }

        // Validate getter/setter parameter counts per ES6 spec
        int paramCount = fn.getParams().size();
        if (entryKind == GET_ENTRY && paramCount != 0) {
            reportError("msg.getter.param.count");
        } else if (entryKind == SET_ENTRY && paramCount != 1) {
            reportError("msg.setter.param.count");
        }
        // ES6 14.3.1: Setter parameter cannot be a rest parameter
        if (entryKind == SET_ENTRY && fn.hasRestParameter()) {
            reportError("msg.setter.rest.param");
        }

        // For constructors, clear the methodDefinition flag after parsing
        // Constructors should be created as closures, not methods (no homeObject needed)
        if (isConstructor) {
            fn.setMethodDefinition(false);
        }

        // The function should be anonymous since we already parsed the name
        Name fnName = fn.getFunctionName();
        if (fnName != null && fnName.length() != 0) {
            reportError("msg.bad.prop");
        }

        // Create the class element
        ClassElement element = new ClassElement(pos);
        element.setPropertyName(propName);
        element.setMethod(fn);
        element.setIsStatic(isStatic);
        element.setIsComputed(propName instanceof ComputedPropertyKey);
        element.setIsPrivate(isPrivate);
        element.setLength(getNodeEnd(fn) - pos);

        // Set up the method form - but NOT for constructors
        if (!isConstructor) {
            // Class methods are shorthand methods (no prototype property)
            fn.setIsShorthand();
            switch (entryKind) {
                case GET_ENTRY:
                    fn.setFunctionIsGetterMethod();
                    break;
                case SET_ENTRY:
                    fn.setFunctionIsSetterMethod();
                    break;
                case METHOD_ENTRY:
                    fn.setFunctionIsNormalMethod();
                    if (isGenerator) {
                        fn.setIsES6Generator();
                    }
                    break;
            }
        }

        return element;
    }

    /** Parses a class property name (similar to object literal property names). */
    private AstNode classPropertyName() throws IOException {
        int tt = peekToken();
        switch (tt) {
            case Token.NAME:
                return createNameNode();
            case Token.PRIVATE_NAME:
                // Private name - create a Name node with the identifier (without #)
                // The # prefix is tracked by the isPrivate flag on ClassElement
                return createNameNode();
            case Token.STRING:
                return createStringLiteral();
            case Token.NUMBER:
            case Token.BIGINT:
                return createNumericLiteral(tt, true);
            case Token.LB:
                // Computed property name
                consumeToken();
                int pos = ts.tokenBeg;
                AstNode expr = assignExpr();
                mustMatchToken(Token.RB, "msg.no.bracket.index", true);
                ComputedPropertyKey cpk = new ComputedPropertyKey(pos, ts.tokenEnd - pos);
                cpk.setExpression(expr);
                return cpk;
            default:
                // In ES6+, reserved words and keywords are allowed as property names.
                // e.g., class { default() {} } is valid.
                // Exception: 'function' without escapes is not allowed because it would
                // conflict with async function parsing (e.g., 'async function' should error).
                if (TokenStream.isKeyword(
                        ts.getString(), compilerEnv.getLanguageVersion(), inUseStrictDirective)) {
                    // 'function' without escapes would cause ambiguity with async functions
                    if (tt == Token.FUNCTION && !ts.identifierContainsEscape()) {
                        return null;
                    }
                    return createNameNode();
                }
                return null;
        }
    }

    private Name createNameNode(boolean checkActivation, int token, String name) {
        Name nameNode = new Name(ts.tokenBeg, name);
        nameNode.setLineColumnNumber(lineNumber(), columnNumber());
        if (checkActivation) {
            checkActivationName(name, token);
        }
        return nameNode;
    }

    private AstNode arrowFunction(AstNode params, int startLine, int startColumn)
            throws IOException {
        int baseLineno = lineNumber(); // line number where source starts
        int functionSourceStart =
                params != null ? params.getPosition() : -1; // start of "function" kwd

        FunctionNode fnNode = new FunctionNode(functionSourceStart);
        fnNode.setFunctionType(FunctionNode.ARROW_FUNCTION);
        fnNode.setJsDocNode(getAndResetJsDoc());

        // Would prefer not to call createDestructuringAssignment until codegen,
        // but the symbol definitions have to happen now, before body is parsed.
        Map<String, Node> destructuring = new HashMap<>();
        Map<String, AstNode> destructuringDefault = new HashMap<>();
        Set<String> paramNames = new HashSet<>();

        PerFunctionVariables savedVars = new PerFunctionVariables(fnNode);
        // Intentionally not overwriting "insideMethod" - we want to propagate this from the parent
        // function or scope
        try {
            if (params instanceof ParenthesizedExpression) {
                fnNode.setParens(0, params.getLength());
                if (params.getIntProp(Node.TRAILING_COMMA, 0) == 1) {
                    fnNode.putIntProp(Node.TRAILING_COMMA, 1);
                }
                AstNode p = ((ParenthesizedExpression) params).getExpression();
                if (!(p instanceof EmptyExpression)) {
                    arrowFunctionParams(fnNode, p, destructuring, destructuringDefault, paramNames);
                }
            } else {
                arrowFunctionParams(
                        fnNode, params, destructuring, destructuringDefault, paramNames);
            }

            if (!destructuring.isEmpty()) {
                Node destructuringNode = new Node(Token.COMMA);
                // Add assignment helper for each destructuring parameter
                for (Map.Entry<String, Node> param : destructuring.entrySet()) {
                    AstNode defaultValue = null;
                    if (destructuringDefault != null) {
                        defaultValue = destructuringDefault.get(param.getKey());
                    }
                    Node assign =
                            createDestructuringAssignment(
                                    Token.VAR,
                                    param.getValue(),
                                    createName(param.getKey()),
                                    defaultValue);
                    destructuringNode.addChildToBack(assign);
                }
                fnNode.putProp(Node.DESTRUCTURING_PARAMS, destructuringNode);
            }

            AstNode body = parseFunctionBody(FunctionNode.ARROW_FUNCTION, fnNode);
            fnNode.setBody(body);
            int end = functionSourceStart + body.getPosition() + body.getLength();
            fnNode.setRawSourceBounds(functionSourceStart, end);
            fnNode.setLength(end - functionSourceStart);
        } finally {
            savedVars.restore();
        }

        if (fnNode.isGenerator()) {
            reportError("msg.arrowfunction.generator");
            return makeErrorNode();
        }

        fnNode.setSourceName(sourceURI);
        fnNode.setBaseLineno(baseLineno);
        fnNode.setEndLineno(lineNumber());
        fnNode.setLineColumnNumber(startLine, startColumn);

        return fnNode;
    }

    /**
     * Checks if the given node represents an async arrow function call pattern: async(...) or async
     * x where async is an identifier followed by arrow function parameters.
     */
    private boolean isAsyncArrowFunctionCall(AstNode pn) {
        if (pn instanceof FunctionCall) {
            FunctionCall call = (FunctionCall) pn;
            // If there was a line terminator between async and (, it's not a valid async arrow
            if (call.hasLineTerminatorBeforeLp()) {
                return false;
            }
            AstNode target = call.getTarget();
            if (target instanceof Name) {
                Name name = (Name) target;
                // async written with escape sequences is not the async keyword
                if (name.containsEscape()) {
                    return false;
                }
                if ("async".equals(name.getIdentifier())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Parses an async arrow function. Called when we've seen async(...) => or async x => pattern.
     * The pn parameter is a FunctionCall node with target "async".
     */
    private AstNode asyncArrowFunction(AstNode pn, int startLine, int startColumn)
            throws IOException {
        FunctionCall call = (FunctionCall) pn;
        List<AstNode> args = call.getArguments();

        // Convert the function call arguments to arrow function parameter format
        AstNode params;
        if (args == null || args.isEmpty()) {
            // async () => ... - empty params, create a ParenthesizedExpression with EmptyExpression
            ParenthesizedExpression pe = new ParenthesizedExpression();
            pe.setExpression(new EmptyExpression());
            pe.setPosition(call.getLp() + call.getPosition());
            pe.setLength(call.getRp() - call.getLp() + 1);
            params = pe;
        } else if (args.size() == 1) {
            // async (x) => ... or async (x, y) => ... - wrap in ParenthesizedExpression
            ParenthesizedExpression pe = new ParenthesizedExpression();
            pe.setExpression(args.get(0));
            pe.setPosition(call.getLp() + call.getPosition());
            pe.setLength(call.getRp() - call.getLp() + 1);
            params = pe;
        } else {
            // Multiple arguments - create comma expression
            int commaPos = args.get(0).getPosition();
            AstNode commaExpr = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                InfixExpression comma =
                        new InfixExpression(Token.COMMA, commaExpr, args.get(i), commaPos);
                commaExpr = comma;
            }
            ParenthesizedExpression pe = new ParenthesizedExpression();
            pe.setExpression(commaExpr);
            pe.setPosition(call.getLp() + call.getPosition());
            pe.setLength(call.getRp() - call.getLp() + 1);
            params = pe;
        }

        // Create the arrow function and mark it as async
        int baseLineno = lineNumber();
        int functionSourceStart = call.getPosition();

        FunctionNode fnNode = new FunctionNode(functionSourceStart);
        fnNode.setFunctionType(FunctionNode.ARROW_FUNCTION);
        fnNode.setJsDocNode(getAndResetJsDoc());
        fnNode.setIsAsync(); // Mark as async

        Map<String, Node> destructuring = new HashMap<>();
        Map<String, AstNode> destructuringDefault = new HashMap<>();
        Set<String> paramNames = new HashSet<>();

        PerFunctionVariables savedVars = new PerFunctionVariables(fnNode);
        try {
            // Set async context so await expressions are recognized in the body
            inAsyncFunction = true;

            if (params instanceof ParenthesizedExpression) {
                fnNode.setParens(0, params.getLength());
                if (params.getIntProp(Node.TRAILING_COMMA, 0) == 1) {
                    fnNode.putIntProp(Node.TRAILING_COMMA, 1);
                }
                AstNode p = ((ParenthesizedExpression) params).getExpression();
                if (!(p instanceof EmptyExpression)) {
                    arrowFunctionParams(fnNode, p, destructuring, destructuringDefault, paramNames);
                }
            } else {
                arrowFunctionParams(
                        fnNode, params, destructuring, destructuringDefault, paramNames);
            }

            if (!destructuring.isEmpty()) {
                Node destructuringNode = new Node(Token.COMMA);
                for (Map.Entry<String, Node> param : destructuring.entrySet()) {
                    AstNode defaultValue = null;
                    if (destructuringDefault != null) {
                        defaultValue = destructuringDefault.get(param.getKey());
                    }
                    Node assign =
                            createDestructuringAssignment(
                                    Token.VAR,
                                    param.getValue(),
                                    createName(param.getKey()),
                                    defaultValue);
                    destructuringNode.addChildToBack(assign);
                }
                fnNode.putProp(Node.DESTRUCTURING_PARAMS, destructuringNode);
            }

            AstNode body = parseFunctionBody(FunctionNode.ARROW_FUNCTION, fnNode);
            fnNode.setBody(body);
            int end = functionSourceStart + body.getPosition() + body.getLength();
            fnNode.setRawSourceBounds(functionSourceStart, end);
            fnNode.setLength(end - functionSourceStart);
        } finally {
            savedVars.restore();
        }

        if (fnNode.isGenerator()) {
            reportError("msg.arrowfunction.generator");
            return makeErrorNode();
        }

        fnNode.setSourceName(sourceURI);
        fnNode.setBaseLineno(baseLineno);
        fnNode.setEndLineno(lineNumber());
        fnNode.setLineColumnNumber(startLine, startColumn);

        return fnNode;
    }

    /**
     * Parses an async arrow function with a single unparenthesized identifier parameter. Called
     * when we've seen "async x =>" pattern. The => has already been consumed.
     */
    private AstNode asyncArrowFunctionWithParam(
            AstNode param, int asyncPos, int asyncLineno, int asyncColumn) throws IOException {
        int baseLineno = lineNumber();
        int functionSourceStart = asyncPos;

        FunctionNode fnNode = new FunctionNode(functionSourceStart);
        fnNode.setFunctionType(FunctionNode.ARROW_FUNCTION);
        fnNode.setJsDocNode(getAndResetJsDoc());
        fnNode.setIsAsync();

        Map<String, Node> destructuring = new HashMap<>();
        Map<String, AstNode> destructuringDefault = new HashMap<>();
        Set<String> paramNames = new HashSet<>();

        PerFunctionVariables savedVars = new PerFunctionVariables(fnNode);
        try {
            inAsyncFunction = true;

            // Single identifier parameter
            arrowFunctionParams(fnNode, param, destructuring, destructuringDefault, paramNames);

            AstNode body = parseFunctionBody(FunctionNode.ARROW_FUNCTION, fnNode);
            fnNode.setBody(body);
            int end = functionSourceStart + body.getPosition() + body.getLength();
            fnNode.setRawSourceBounds(functionSourceStart, end);
            fnNode.setLength(end - functionSourceStart);
        } finally {
            savedVars.restore();
        }

        if (fnNode.isGenerator()) {
            reportError("msg.arrowfunction.generator");
            return makeErrorNode();
        }

        fnNode.setSourceName(sourceURI);
        fnNode.setBaseLineno(baseLineno);
        fnNode.setEndLineno(lineNumber());
        fnNode.setLineColumnNumber(asyncLineno, asyncColumn);

        return fnNode;
    }

    /**
     * Checks if any names in a destructuring pattern duplicate existing parameter names or each
     * other. Reports errors for any duplicates found (arrow functions are always strict w.r.t.
     * duplicate params). The check for eval/arguments only applies in strict mode.
     */
    private void checkDestructuringDuplicates(AstNode pattern, Set<String> paramNames) {
        // Use a list to detect duplicates within the pattern itself
        List<String> patternNames = new ArrayList<>();
        collectDestructuringNamesToList(pattern, patternNames);
        for (String name : patternNames) {
            // eval/arguments check only in strict mode
            if (this.inUseStrictDirective) {
                if ("eval".equals(name) || "arguments".equals(name)) {
                    reportError("msg.bad.id.strict", name);
                }
            }
            // Duplicate check always applies for arrow functions (ES6 14.2.1)
            if (paramNames.contains(name)) {
                addError("msg.dup.param.strict", name);
            } else {
                paramNames.add(name);
            }
        }
    }

    /** Collects all bound names from a destructuring pattern into a List (preserves duplicates). */
    private void collectDestructuringNamesToList(AstNode node, List<String> names) {
        if (node instanceof Name) {
            names.add(((Name) node).getIdentifier());
        } else if (node instanceof ArrayLiteral) {
            for (AstNode elem : ((ArrayLiteral) node).getElements()) {
                if (elem != null && elem.getType() != Token.EMPTY) {
                    collectDestructuringNamesToList(elem, names);
                }
            }
        } else if (node instanceof ObjectLiteral) {
            for (AstNode elem : ((ObjectLiteral) node).getElements()) {
                if (elem instanceof ObjectProperty) {
                    ObjectProperty prop = (ObjectProperty) elem;
                    AstNode value = prop.getValue();
                    if (value != null) {
                        collectDestructuringNamesToList(value, names);
                    } else {
                        // Shorthand property like {x} - the key is the name
                        collectDestructuringNamesToList(prop.getKey(), names);
                    }
                }
            }
        } else if (node instanceof Assignment) {
            // Default value like [x = 1] - collect from left side
            collectDestructuringNamesToList(((Assignment) node).getLeft(), names);
        } else if (node.getType() == Token.DOTDOTDOT) {
            // Rest element like [...x] - collect the underlying name
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof AstNode) {
                    collectDestructuringNamesToList((AstNode) child, names);
                }
            }
        }
    }

    private void arrowFunctionParams(
            FunctionNode fnNode,
            AstNode params,
            Map<String, Node> destructuring,
            Map<String, AstNode> destructuringDefault,
            Set<String> paramNames)
            throws IOException {
        if (params instanceof ArrayLiteral || params instanceof ObjectLiteral) {
            // Check for duplicate names in destructuring pattern (arrow functions are always
            // strict)
            checkDestructuringDuplicates(params, paramNames);
            markDestructuring(params);
            fnNode.addParam(params);
            String pname = currentScriptOrFn.getNextTempName();
            defineSymbol(Token.LP, pname, false);
            destructuring.put(pname, params);
        } else if (params instanceof InfixExpression && params.getType() == Token.COMMA) {
            arrowFunctionParams(
                    fnNode,
                    ((InfixExpression) params).getLeft(),
                    destructuring,
                    destructuringDefault,
                    paramNames);
            arrowFunctionParams(
                    fnNode,
                    ((InfixExpression) params).getRight(),
                    destructuring,
                    destructuringDefault,
                    paramNames);
        } else if (params instanceof Name) {
            fnNode.addParam(params);
            String paramName = ((Name) params).getIdentifier();
            defineSymbol(Token.LP, paramName);

            // await cannot be used as parameter name in async functions
            if (fnNode.isAsync() && "await".equals(paramName)) {
                reportError("msg.syntax");
            }

            // eval/arguments check only in strict mode
            if (this.inUseStrictDirective) {
                if ("eval".equals(paramName) || "arguments".equals(paramName)) {
                    reportError("msg.bad.id.strict", paramName);
                }
            }
            // Duplicate check always applies for arrow functions (ES6 14.2.1 Early Errors:
            // ArrowFormalParameters must not contain any duplicate parameters)
            if (paramNames.contains(paramName)) {
                addError("msg.dup.param.strict", paramName);
            }
            paramNames.add(paramName);
        } else if (params instanceof Assignment) {
            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                AstNode rhs = ((Assignment) params).getRight();
                AstNode lhs = ((Assignment) params).getLeft();
                String paramName;

                /* copy default values for use in IR */
                if (lhs instanceof Name) {
                    paramName = ((Name) lhs).getIdentifier();
                    fnNode.putDefaultParams(paramName, rhs);
                    arrowFunctionParams(
                            fnNode, lhs, destructuring, destructuringDefault, paramNames);
                } else if (lhs instanceof ArrayLiteral || lhs instanceof ObjectLiteral) {
                    // Check for duplicate names in destructuring pattern (arrow functions are
                    // always strict)
                    checkDestructuringDuplicates(lhs, paramNames);
                    markDestructuring(lhs);
                    fnNode.addParam(lhs);
                    String pname = currentScriptOrFn.getNextTempName();
                    defineSymbol(Token.LP, pname, false);
                    destructuring.put(pname, lhs);
                    destructuringDefault.put(pname, rhs);
                } else {
                    reportError("msg.no.parm", params.getPosition(), params.getLength());
                    fnNode.addParam(makeErrorNode());
                }
            } else {
                reportError("msg.default.args");
            }
        } else {
            reportError("msg.no.parm", params.getPosition(), params.getLength());
            fnNode.addParam(makeErrorNode());
        }
    }

    // This function does not match the closing RC: the caller matches
    // the RC so it can provide a suitable error message if not matched.
    // This means it's up to the caller to set the length of the node to
    // include the closing RC.  The node start pos is set to the
    // absolute buffer start position, and the caller should fix it up
    // to be relative to the parent node.  All children of this block
    // node are given relative start positions and correct lengths.

    private AstNode statements(AstNode parent) throws IOException {
        if (currentToken != Token.LC // assertion can be invalid in bad code
                && !compilerEnv.isIdeMode()) codeBug();
        int pos = ts.tokenBeg;
        AstNode block = parent != null ? parent : new Block(pos);
        block.setLineColumnNumber(lineNumber(), columnNumber());

        ++nestingOfStatement;
        try {
            int tt;
            while ((tt = peekToken()) > Token.EOF && tt != Token.RC) {
                block.addChild(statement());
            }
            block.setLength(ts.tokenBeg - pos);
            return block;
        } finally {
            --nestingOfStatement;
        }
    }

    private AstNode statements() throws IOException {
        return statements(null);
    }

    private static class ConditionData {
        AstNode condition;
        int lp = -1;
        int rp = -1;
    }

    // parse and return a parenthesized expression
    private ConditionData condition() throws IOException {
        ConditionData data = new ConditionData();

        if (mustMatchToken(Token.LP, "msg.no.paren.cond", true)) data.lp = ts.tokenBeg;

        data.condition = expr(false);

        if (mustMatchToken(Token.RP, "msg.no.paren.after.cond", true)) data.rp = ts.tokenBeg;

        // Report strict warning on code like "if (a = 7) ...". Suppress the
        // warning if the condition is parenthesized, like "if ((a = 7)) ...".
        if (data.condition instanceof Assignment) {
            addStrictWarning(
                    "msg.equal.as.assign",
                    "",
                    data.condition.getPosition(),
                    data.condition.getLength());
        }
        return data;
    }

    private AstNode statement() throws IOException {
        int pos = ts.tokenBeg;
        try {
            AstNode pn = statementHelper();
            if (pn != null) {
                if (compilerEnv.isStrictMode() && !pn.hasSideEffects()) {
                    int beg = pn.getPosition();
                    beg = Math.max(beg, lineBeginningFor(beg));
                    addStrictWarning(
                            pn instanceof EmptyStatement
                                    ? "msg.extra.trailing.semi"
                                    : "msg.no.side.effects",
                            "",
                            beg,
                            nodeEnd(pn) - beg);
                }
                int ntt = peekToken();
                if (ntt == Token.COMMENT
                        && pn.getLineno()
                                == scannedComments.get(scannedComments.size() - 1).getLineno()) {
                    pn.setInlineComment(scannedComments.get(scannedComments.size() - 1));
                    consumeToken();
                }
                return pn;
            }
        } catch (ParserException e) {
            // an ErrorNode was added to the ErrorReporter
        }

        // error:  skip ahead to a probable statement boundary
        guessingStatementEnd:
        for (; ; ) {
            int tt = peekTokenOrEOL();
            consumeToken();
            switch (tt) {
                case Token.ERROR:
                case Token.EOF:
                case Token.EOL:
                case Token.SEMI:
                    break guessingStatementEnd;
            }
        }
        // We don't make error nodes explicitly part of the tree;
        // they get added to the ErrorReporter.  May need to do
        // something different here.
        return new EmptyStatement(pos, ts.tokenBeg - pos);
    }

    private AstNode statementHelper() throws IOException {
        // If the statement is set, then it's been told its label by now.
        if (currentLabel != null && currentLabel.getStatement() != null) currentLabel = null;

        AstNode pn = null;
        int tt = peekToken(), pos = ts.tokenBeg;
        int lineno, column;

        switch (tt) {
            case Token.IF:
                return ifStatement();

            case Token.SWITCH:
                return switchStatement();

            case Token.WHILE:
                return whileLoop();

            case Token.DO:
                return doLoop();

            case Token.FOR:
                return forLoop();

            case Token.TRY:
                return tryStatement();

            case Token.THROW:
                pn = throwStatement();
                break;

            case Token.BREAK:
                pn = breakStatement();
                break;

            case Token.CONTINUE:
                pn = continueStatement();
                break;

            case Token.WITH:
                if (this.inUseStrictDirective) {
                    reportError("msg.no.with.strict");
                }
                return withStatement();

            case Token.IMPORT:
                if (!parsingModule) {
                    reportError("msg.import.not.module");
                }
                if (nestingOfStatement > 0 || nestingOfFunction > 0) {
                    reportError("msg.import.decl.at.top.level");
                }
                return parseImport();

            case Token.EXPORT:
                if (!parsingModule) {
                    reportError("msg.export.not.module");
                }
                if (nestingOfStatement > 0 || nestingOfFunction > 0) {
                    reportError("msg.export.decl.at.top.level");
                }
                return parseExport();

            case Token.CONST:
            case Token.VAR:
                consumeToken();
                lineno = lineNumber();
                column = columnNumber();
                pn = variables(currentToken, ts.tokenBeg, true);
                pn.setLineColumnNumber(lineno, column);
                break;

            case Token.LET:
                pn = letStatementOrIdentifier();
                if (pn instanceof VariableDeclaration && peekToken() == Token.SEMI) break;
                return pn;

            case Token.RETURN:
                pn = returnOrYield(tt, false);
                break;
            case Token.YIELD:
                // In ES6 generators, yield can appear in comma expressions at statement level.
                // We handle this by falling through to the default expression parsing,
                // which will properly handle "yield, yield" as a comma expression.
                // assignExpr() will then call returnOrYield() to parse each yield.
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                        && isCurrentFunctionGenerator()) {
                    lineno = ts.getLineno();
                    column = ts.getTokenColumn();
                    pn = new ExpressionStatement(expr(false), !insideFunctionBody());
                    pn.setLineColumnNumber(lineno, column);
                    break;
                }
                pn = returnOrYield(tt, false);
                break;

            case Token.DEBUGGER:
                consumeToken();
                pn = new KeywordLiteral(ts.tokenBeg, ts.tokenEnd - ts.tokenBeg, tt);
                pn.setLineColumnNumber(lineNumber(), columnNumber());
                break;

            case Token.LC:
                return block();

            case Token.ERROR:
                consumeToken();
                return makeErrorNode();

            case Token.SEMI:
                consumeToken();
                pos = ts.tokenBeg;
                pn = new EmptyStatement(pos, ts.tokenEnd - pos);
                pn.setLineColumnNumber(lineNumber(), columnNumber());
                return pn;

            case Token.FUNCTION:
                consumeToken();
                return function(FunctionNode.FUNCTION_EXPRESSION_STATEMENT);

            case Token.ASYNC:
                // Check if this is 'async function' (with no line terminator between)
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    // Look ahead to determine if this is 'async function'
                    // We need to check without consuming 'async' first
                    consumeToken();
                    boolean asyncContainsEscape = ts.identifierContainsEscape();
                    int asyncTT = peekTokenOrEOL();
                    // Only treat as async function if async was not escaped
                    if (!asyncContainsEscape
                            && asyncTT != Token.EOL
                            && peekToken() == Token.FUNCTION) {
                        // This is 'async function' - parse as async function declaration
                        consumeToken();
                        boolean isGenerator = matchToken(Token.MUL, true);
                        return function(
                                FunctionNode.FUNCTION_EXPRESSION_STATEMENT,
                                false,
                                isGenerator,
                                true);
                    }

                    // Not 'async function' - put back the async token by restarting
                    // expression parsing will see 'async' as ASYNC token in primaryExpr
                    // Since we already consumed 'async', we need to handle this specially
                    int asyncPos = ts.tokenBeg;
                    int asyncLineno = ts.getLineno();
                    int asyncColumn = ts.getTokenColumn();

                    // Create name node for 'async'
                    saveNameTokenData(
                            asyncPos, "async", asyncLineno, asyncColumn, asyncContainsEscape);
                    AstNode asyncName = createNameNode(true, Token.NAME);

                    // Continue with member expression parsing (handles (...) for calls)
                    AstNode memberResult = memberExprTail(true, asyncName);

                    // Handle postfix ++/-- (from unaryExpr)
                    asyncTT = peekTokenOrEOL();
                    if (asyncTT == Token.INC || asyncTT == Token.DEC) {
                        consumeToken();
                        memberResult =
                                new UpdateExpression(asyncTT, ts.tokenBeg, memberResult, true);
                        ((UpdateExpression) memberResult)
                                .setLineColumnNumber(
                                        memberResult.getLineno(), memberResult.getColumn());
                        checkBadIncDec((UpdateExpression) memberResult);
                    }

                    // Chain through binary operators via the expr continuation method
                    pn = exprContinuation(memberResult);
                    pn = new ExpressionStatement(pn, !insideFunctionBody());
                    pn.setLineColumnNumber(asyncLineno, asyncColumn);
                    break;
                }
                // Fall through to default if not ES6+
                lineno = ts.getLineno();
                column = ts.getTokenColumn();
                pn = new ExpressionStatement(expr(false), !insideFunctionBody());
                pn.setLineColumnNumber(lineno, column);
                break;

            case Token.CLASS:
                consumeToken();
                return classDeclaration(ClassNode.CLASS_STATEMENT);

            case Token.DEFAULT:
                pn = defaultXmlNamespace();
                break;

            case Token.NAME:
                pn = nameOrLabel();
                if (pn instanceof ExpressionStatement) break;
                return pn; // LabeledStatement
            case Token.AWAIT:
                // Outside async functions and module code, 'await' can be used as identifier/label
                // In module code, 'await' is always reserved (ES2017 12.1.1)
                if (!inAsyncFunction && !parsingModule) {
                    pn = awaitNameOrLabel();
                    if (pn instanceof ExpressionStatement) break;
                    return pn; // LabeledStatement
                }
                // Inside async functions or module code, fall through for expression parsing
                lineno = ts.getLineno();
                column = ts.getTokenColumn();
                pn = new ExpressionStatement(expr(false), !insideFunctionBody());
                pn.setLineColumnNumber(lineno, column);
                break;
            case Token.COMMENT:
                // Do not consume token here
                pn = scannedComments.get(scannedComments.size() - 1);
                return pn;
            default:
                // Intentionally not calling lineNumber/columnNumber here!
                // We have not consumed any token yet, so the position would be invalid
                lineno = ts.getLineno();
                column = ts.getTokenColumn();
                pn = new ExpressionStatement(expr(false), !insideFunctionBody());
                pn.setLineColumnNumber(lineno, column);
                break;
        }

        autoInsertSemicolon(pn);
        return pn;
    }

    private void autoInsertSemicolon(AstNode pn) throws IOException {
        int ttFlagged = peekFlaggedToken();
        int pos = pn.getPosition();
        switch (ttFlagged & CLEAR_TI_MASK) {
            case Token.SEMI:
                // Consume ';' as a part of expression
                consumeToken();
                // extend the node bounds to include the semicolon.
                pn.setLength(ts.tokenEnd - pos);
                break;
            case Token.ERROR:
            case Token.EOF:
            case Token.RC:
                // Autoinsert ;
                // Token.EOF can have negative length and negative nodeEnd(pn).
                // So, make the end position at least pos+1.
                warnMissingSemi(pos, Math.max(pos + 1, nodeEnd(pn)));
                break;
            default:
                if ((ttFlagged & TI_AFTER_EOL) == 0) {
                    // Report error if no EOL or autoinsert ; otherwise
                    reportError("msg.no.semi.stmt");
                } else {
                    warnMissingSemi(pos, nodeEnd(pn));
                }
                break;
        }
    }

    private IfStatement ifStatement() throws IOException {
        if (currentToken != Token.IF) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, lineno = lineNumber(), elsePos = -1, column = columnNumber();
        IfStatement pn = new IfStatement(pos);
        ConditionData data = condition();
        AstNode ifTrue = getNextStatementAfterInlineComments(pn), ifFalse = null;
        // In strict mode, function declarations cannot be the body of an if statement
        if (inUseStrictDirective
                && ifTrue instanceof FunctionNode
                && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
            reportError("msg.func.decl.if.strict");
        }
        if (matchToken(Token.ELSE, true)) {
            int tt = peekToken();
            if (tt == Token.COMMENT) {
                pn.setElseKeyWordInlineComment(scannedComments.get(scannedComments.size() - 1));
                consumeToken();
            }
            elsePos = ts.tokenBeg - pos;
            ifFalse = getNextStatementAfterInlineComments(null);
            // Same check for else branch
            if (inUseStrictDirective
                    && ifFalse instanceof FunctionNode
                    && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                reportError("msg.func.decl.if.strict");
            }
        }
        int end = getNodeEnd(ifFalse != null ? ifFalse : ifTrue);
        pn.setLength(end - pos);
        pn.setCondition(data.condition);
        pn.setParens(data.lp - pos, data.rp - pos);
        pn.setThenPart(ifTrue);
        pn.setElsePart(ifFalse);
        pn.setElsePosition(elsePos);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private SwitchStatement switchStatement() throws IOException {
        if (currentToken != Token.SWITCH) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;

        SwitchStatement pn = new SwitchStatement(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        pushScope(pn);
        try {
            if (mustMatchToken(Token.LP, "msg.no.paren.switch", true)) pn.setLp(ts.tokenBeg - pos);

            AstNode discriminant = expr(false);
            pn.setExpression(discriminant);
            enterSwitch(pn);

            try {
                if (mustMatchToken(Token.RP, "msg.no.paren.after.switch", true))
                    pn.setRp(ts.tokenBeg - pos);

                mustMatchToken(Token.LC, "msg.no.brace.switch", true);

                boolean hasDefault = false;
                int tt;
                switchLoop:
                for (; ; ) {
                    tt = nextToken();
                    int casePos = ts.tokenBeg;
                    int caseLineno = lineNumber(), caseColumn = columnNumber();
                    AstNode caseExpression = null;
                    switch (tt) {
                        case Token.RC:
                            pn.setLength(ts.tokenEnd - pos);
                            break switchLoop;

                        case Token.CASE:
                            caseExpression = expr(false);
                            mustMatchToken(Token.COLON, "msg.no.colon.case", true);
                            break;

                        case Token.DEFAULT:
                            if (hasDefault) {
                                reportError("msg.double.switch.default");
                            }
                            hasDefault = true;
                            mustMatchToken(Token.COLON, "msg.no.colon.case", true);
                            break;
                        case Token.COMMENT:
                            AstNode n = scannedComments.get(scannedComments.size() - 1);
                            pn.addChild(n);
                            continue switchLoop;
                        default:
                            reportError("msg.bad.switch");
                            break switchLoop;
                    }

                    SwitchCase caseNode = new SwitchCase(casePos);
                    caseNode.setExpression(caseExpression);
                    caseNode.setLength(ts.tokenEnd - pos); // include colon
                    caseNode.setLineColumnNumber(caseLineno, caseColumn);

                    ++nestingOfStatement;
                    try {
                        while ((tt = peekToken()) != Token.RC
                                && tt != Token.CASE
                                && tt != Token.DEFAULT
                                && tt != Token.EOF) {
                            if (tt == Token.COMMENT) {
                                Comment inlineComment =
                                        scannedComments.get(scannedComments.size() - 1);
                                if (caseNode.getInlineComment() == null
                                        && inlineComment.getLineno() == caseNode.getLineno()) {
                                    caseNode.setInlineComment(inlineComment);
                                } else {
                                    caseNode.addStatement(inlineComment);
                                }
                                consumeToken();
                                continue;
                            }
                            AstNode nextStmt = statement();
                            caseNode.addStatement(nextStmt); // updates length
                        }
                    } finally {
                        --nestingOfStatement;
                    }
                    pn.addCase(caseNode);
                }
            } finally {
                exitSwitch();
            }
            return pn;
        } finally {
            popScope();
        }
    }

    private WhileLoop whileLoop() throws IOException {
        if (currentToken != Token.WHILE) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;
        WhileLoop pn = new WhileLoop(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        enterLoop(pn);
        try {
            ConditionData data = condition();
            pn.setCondition(data.condition);
            pn.setParens(data.lp - pos, data.rp - pos);
            AstNode body = getNextStatementAfterInlineComments(pn);
            // Function declarations are not allowed as while body (ES6 13.7.3)
            if (body instanceof FunctionNode
                    && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                reportError("msg.func.decl.not.stmt.position");
            }
            pn.setLength(getNodeEnd(body) - pos);
            restoreRelativeLoopPosition(pn);
            pn.setBody(body);
        } finally {
            exitLoop();
        }
        return pn;
    }

    private DoLoop doLoop() throws IOException {
        if (currentToken != Token.DO) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, end;
        DoLoop pn = new DoLoop(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        enterLoop(pn);
        try {
            AstNode body = getNextStatementAfterInlineComments(pn);
            // Function declarations are not allowed as do-while body (ES6 13.7.2)
            if (body instanceof FunctionNode
                    && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                reportError("msg.func.decl.not.stmt.position");
            }
            mustMatchToken(Token.WHILE, "msg.no.while.do", true);
            pn.setWhilePosition(ts.tokenBeg - pos);
            ConditionData data = condition();
            pn.setCondition(data.condition);
            pn.setParens(data.lp - pos, data.rp - pos);
            end = getNodeEnd(body);
            restoreRelativeLoopPosition(pn);
            pn.setBody(body);
        } finally {
            exitLoop();
        }
        // Always auto-insert semicolon to follow SpiderMonkey:
        // It is required by ECMAScript but is ignored by the rest of
        // world, see bug 238945
        if (matchToken(Token.SEMI, true)) {
            end = ts.tokenEnd;
        }
        pn.setLength(end - pos);
        return pn;
    }

    private int peekUntilNonComment(int tt) throws IOException {
        while (tt == Token.COMMENT) {
            consumeToken();
            tt = peekToken();
        }
        return tt;
    }

    private AstNode getNextStatementAfterInlineComments(AstNode pn) throws IOException {
        boolean savedSingleStatementContext = inSingleStatementContext;
        inSingleStatementContext = true;
        ++nestingOfStatement;
        try {
            AstNode body = statement();
            if (Token.COMMENT == body.getType()) {
                AstNode commentNode = body;
                body = statement();
                if (pn != null) {
                    pn.setInlineComment(commentNode);
                } else {
                    body.setInlineComment(commentNode);
                }
            }
            // Lexical declarations (let/const) are not allowed as the body of control structures
            // They must be wrapped in a block. ES6 13.6.0.1, 13.7.0.1, etc.
            // Only enforce this in ES6+ mode; older versions allow non-standard behavior.
            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                    && body instanceof VariableDeclaration) {
                VariableDeclaration vd = (VariableDeclaration) body;
                if (vd.getType() == Token.LET || vd.getType() == Token.CONST) {
                    reportError("msg.lexical.decl.not.in.block");
                }
            }
            // Labelled function statements are not allowed as the body of control structures.
            // ES6 13.7.0.1 - It is a Syntax Error if IsLabelledFunction(Statement) is true.
            if (isLabelledFunction(body)) {
                reportError("msg.labelled.function.stmt");
            }
            // Generator and async function declarations are not allowed in statement position
            // ES6 13.7.0.1 - Only regular function declarations are allowed, not generators
            // ES2017 - Async function declarations are also not allowed in statement position
            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                    && body instanceof FunctionNode) {
                FunctionNode fn = (FunctionNode) body;
                if (fn.isES6Generator()) {
                    reportError("msg.generator.decl.not.in.block");
                } else if (fn.isAsync()) {
                    reportError("msg.async.decl.not.in.block");
                }
            }
            // Class declarations are not allowed in statement position
            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                    && body instanceof ClassNode) {
                reportError("msg.class.decl.not.in.block");
            }
            return body;
        } finally {
            --nestingOfStatement;
            inSingleStatementContext = savedSingleStatementContext;
        }
    }

    /**
     * Checks if the given node is a labelled function, i.e., a LabeledStatement whose ultimate
     * statement is a function declaration. ES6 IsLabelledFunction abstract operation.
     */
    private boolean isLabelledFunction(AstNode node) {
        if (node instanceof LabeledStatement) {
            AstNode stmt = ((LabeledStatement) node).getStatement();
            return stmt instanceof FunctionNode || isLabelledFunction(stmt);
        }
        return false;
    }

    private Loop forLoop() throws IOException {
        if (currentToken != Token.FOR) codeBug();
        consumeToken();
        int forPos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        boolean isForEach = false, isForIn = false, isForOf = false, isForAwaitOf = false;
        int eachPos = -1, inPos = -1, lp = -1, rp = -1, awaitPos = -1;
        AstNode init = null; // init is also foo in 'foo in object'
        AstNode cond = null; // cond is also object in 'foo in object'
        AstNode incr = null;
        Loop pn = null;

        Scope tempScope = new Scope();
        pushScope(tempScope); // decide below what AST class to use
        try {
            // See if this is a for each () or for await () instead of just a for ()
            // In async functions, 'await' is tokenized as Token.AWAIT, not Token.NAME
            if (inAsyncFunction && matchToken(Token.AWAIT, true)) {
                // ES2018 for-await-of: for await (... of ...)
                isForAwaitOf = true;
                awaitPos = ts.tokenBeg - forPos;
            } else if (matchToken(Token.NAME, true)) {
                String name = ts.getString();
                if ("each".equals(name)) {
                    isForEach = true;
                    eachPos = ts.tokenBeg - forPos;
                } else if ("await".equals(name) && !ts.identifierContainsEscape()) {
                    // for-await-of used outside async function is an error
                    reportError("msg.bad.await");
                    isForAwaitOf = true;
                    awaitPos = ts.tokenBeg - forPos;
                } else {
                    reportError("msg.no.paren.for");
                }
            }

            if (mustMatchToken(Token.LP, "msg.no.paren.for", true)) lp = ts.tokenBeg - forPos;
            int tt = peekToken();

            init = forLoopInit(tt);
            if (matchToken(Token.IN, true)) {
                if (isForAwaitOf) {
                    // for-await-of requires 'of', not 'in'
                    reportError("msg.syntax");
                }
                isForIn = true;
                inPos = ts.tokenBeg - forPos;
                markDestructuring(init);
                cond = expr(false); // object over which we're iterating
            } else if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                    && matchToken(Token.NAME, true)
                    && "of".equals(ts.getString())
                    && !ts.identifierContainsEscape()) {
                // ES6 12.1.1: The `of` keyword must not contain escape sequences
                // isForAwaitOf implies isForOf (for-await-of is a special case of for-of)
                isForOf = true;
                inPos = ts.tokenBeg - forPos;
                markDestructuring(init);
                // ES6 13.7.5.1: for-of requires AssignmentExpression, not Expression
                // This disallows comma expressions like: for (x of a, b)
                cond = assignExpr();
            } else if (isForAwaitOf) {
                // for-await requires 'of' keyword
                reportError("msg.syntax");
                cond = assignExpr();
            } else { // ordinary for-loop
                // For ordinary for loops, destructuring declarations must have initializers
                if (init instanceof VariableDeclaration) {
                    VariableDeclaration varDecl = (VariableDeclaration) init;
                    for (VariableInitializer vi : varDecl.getVariables()) {
                        if (vi.isDestructuring() && vi.getInitializer() == null) {
                            reportError("msg.destruct.assign.no.init");
                        }
                    }
                }

                mustMatchToken(Token.SEMI, "msg.no.semi.for", true);
                if (peekToken() == Token.SEMI) {
                    // no loop condition
                    cond = new EmptyExpression(ts.tokenBeg, 1);
                    // We haven't consumed the token, so we need the CURRENT lexer position
                    cond.setLineColumnNumber(ts.getLineno(), ts.getTokenColumn());
                } else {
                    cond = expr(false);
                }

                mustMatchToken(Token.SEMI, "msg.no.semi.for.cond", true);
                int tmpPos = ts.tokenEnd;
                if (peekToken() == Token.RP) {
                    incr = new EmptyExpression(tmpPos, 1);
                    // We haven't consumed the token, so we need the CURRENT lexer position
                    incr.setLineColumnNumber(ts.getLineno(), ts.getTokenColumn());
                } else {
                    incr = expr(false);
                }
            }

            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl", true)) rp = ts.tokenBeg - forPos;

            if (isForIn || isForOf || isForAwaitOf) {
                ForInLoop fis = new ForInLoop(forPos);
                if (init instanceof VariableDeclaration) {
                    VariableDeclaration varDecl = (VariableDeclaration) init;
                    // check that there was only one variable given
                    if (varDecl.getVariables().size() > 1) {
                        reportError("msg.mult.index");
                    }
                    // check that let/const declarations don't have initializers in for-in/for-of
                    // ES6: var declarations also cannot have initializers in for-of
                    // (but are allowed in for-in for Annex B compatibility)
                    boolean disallowInit =
                            (varDecl.getType() == Token.LET || varDecl.getType() == Token.CONST)
                                    || ((isForOf || isForAwaitOf)
                                            && varDecl.getType() == Token.VAR);
                    if (disallowInit) {
                        for (VariableInitializer vi : varDecl.getVariables()) {
                            if (vi.getInitializer() != null) {
                                reportError("msg.invalid.for.in.init");
                                break;
                            }
                        }
                    }
                }
                if ((isForOf || isForAwaitOf) && isForEach) {
                    reportError("msg.invalid.for.each");
                }
                fis.setIterator(init);
                fis.setIteratedObject(cond);
                fis.setInPosition(inPos);
                fis.setIsForEach(isForEach);
                fis.setEachPosition(eachPos);
                fis.setIsForOf(isForOf);
                fis.setIsForAwaitOf(isForAwaitOf);
                fis.setAwaitPosition(awaitPos);
                pn = fis;
            } else {
                ForLoop fl = new ForLoop(forPos);
                fl.setInitializer(init);
                fl.setCondition(cond);
                fl.setIncrement(incr);
                pn = fl;
            }

            // replace temp scope with the new loop object
            currentScope.replaceWith(pn);
            popScope();

            // We have to parse the body -after- creating the loop node,
            // so that the loop node appears in the loopSet, allowing
            // break/continue statements to find the enclosing loop.
            enterLoop(pn);
            try {
                AstNode body = getNextStatementAfterInlineComments(pn);
                // Function declarations are not allowed as for/for-in/for-of body (ES6 13.7)
                if (body instanceof FunctionNode
                        && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    reportError("msg.func.decl.not.stmt.position");
                }
                pn.setLength(getNodeEnd(body) - forPos);
                restoreRelativeLoopPosition(pn);
                pn.setBody(body);
            } finally {
                exitLoop();
            }

        } finally {
            if (currentScope == tempScope) {
                popScope();
            }
        }
        pn.setParens(lp, rp);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private AstNode forLoopInit(int tt) throws IOException {
        try {
            inForInit = true; // checked by variables() and relExpr()
            AstNode init = null;
            if (tt == Token.SEMI) {
                init = new EmptyExpression(ts.tokenBeg, 1);
                // We haven't consumed the token, so we need the CURRENT lexer position
                init.setLineColumnNumber(ts.getLineno(), ts.getTokenColumn());
            } else if (tt == Token.VAR || tt == Token.CONST) {
                consumeToken();
                init = variables(tt, ts.tokenBeg, false);
            } else if (tt == Token.LET) {
                // In ES6 non-strict mode, 'let' might be an identifier, not a declaration
                // Per ES6: 'let [' is always a declaration; otherwise check if next can start
                // binding
                // In pre-ES6 (e.g., JS1.7), 'let' is always a keyword
                if (inUseStrictDirective
                        || compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
                    consumeToken();
                    init = variables(tt, ts.tokenBeg, false);
                } else {
                    // ES6 non-strict mode: Peek ahead to see what follows 'let'
                    consumeToken();
                    int letPos = ts.tokenBeg;
                    int letLineno = lineNumber();
                    int letColumn = columnNumber();
                    int nextTT = peekToken();

                    // 'let [' is always a declaration (lookahead restriction)
                    if (nextTT == Token.LB) {
                        init = variables(Token.LET, letPos, false);
                    } else if (nextTT == Token.NAME
                            || nextTT == Token.LC
                            || nextTT == Token.YIELD
                            || nextTT == Token.LET) {
                        // Next token can start a binding, so it's a let declaration
                        init = variables(Token.LET, letPos, false);
                    } else {
                        // Next token can't start a binding, so 'let' is an identifier
                        // Save token info and parse as expression
                        saveNameTokenData(letPos, "let", letLineno, letColumn);
                        AstNode letName = createNameNode(true, Token.NAME);
                        init = memberExprTail(false, letName);
                        // Continue parsing for assignment operators, etc.
                        init = assignExprTail(init);
                    }
                }
            } else {
                init = expr(false);
            }
            return init;
        } finally {
            inForInit = false;
        }
    }

    /** Continue parsing assignment expression after we have the left-hand side */
    private AstNode assignExprTail(AstNode pn) throws IOException {
        int tt = peekToken();
        if (Token.FIRST_ASSIGN <= tt && tt <= Token.LAST_ASSIGN) {
            consumeToken();
            markDestructuring(pn);
            int opPos = ts.tokenBeg;
            if (isNotValidSimpleAssignmentTarget(pn)) {
                reportError("msg.syntax.invalid.assignment.lhs");
            }
            pn = new Assignment(tt, pn, assignExpr(), opPos);
        }
        return pn;
    }

    private TryStatement tryStatement() throws IOException {
        if (currentToken != Token.TRY) codeBug();
        consumeToken();

        // Pull out JSDoc info and reset it before recursing.
        Comment jsdocNode = getAndResetJsDoc();

        int tryPos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber(), finallyPos = -1;

        TryStatement pn = new TryStatement(tryPos);
        // Hnadled comment here because there should not be try without LC
        int lctt = peekToken();
        while (lctt == Token.COMMENT) {
            Comment commentNode = scannedComments.get(scannedComments.size() - 1);
            pn.setInlineComment(commentNode);
            consumeToken();
            lctt = peekToken();
        }
        if (lctt != Token.LC) {
            reportError("msg.no.brace.try");
        }
        AstNode tryBlock = getNextStatementAfterInlineComments(pn);
        int tryEnd = getNodeEnd(tryBlock);

        List<CatchClause> clauses = null;

        boolean sawDefaultCatch = false;
        int peek = peekToken();
        while (peek == Token.COMMENT) {
            Comment commentNode = scannedComments.get(scannedComments.size() - 1);
            pn.setInlineComment(commentNode);
            consumeToken();
            peek = peekToken();
        }

        boolean previous = hasUndefinedBeenRedefined;
        if (peek == Token.CATCH) {
            while (matchToken(Token.CATCH, true)) {
                int catchLineNum = lineNumber();
                if (sawDefaultCatch) {
                    reportError("msg.catch.unreachable");
                }
                int catchPos = ts.tokenBeg,
                        lp = -1,
                        rp = -1,
                        guardPos = -1,
                        catchLine = lineNumber(),
                        catchColumn = columnNumber();
                AstNode varName = null;
                AstNode catchCond = null;

                switch (peekToken()) {
                    case Token.LP:
                        {
                            matchToken(Token.LP, true);
                            lp = ts.tokenBeg;

                            int tt = peekToken();
                            if (tt == Token.LB || tt == Token.LC) {
                                // Destructuring pattern
                                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                                    varName = destructuringPrimaryExpr();
                                    markDestructuring(varName);
                                } else {
                                    reportError("msg.catch.destructuring.requires.es6");
                                }
                            } else {
                                // Simple identifier
                                // In non-strict mode, 'let' can be used as an identifier
                                // Reuse tt from above (line 2601)
                                if (tt == Token.UNDEFINED || isIdentifierToken(tt)) {
                                    consumeToken();
                                } else {
                                    mustMatchIdentifier("msg.bad.catchcond", true);
                                }

                                varName = createNameNode();
                                Comment jsdocNodeForName = getAndResetJsDoc();
                                if (jsdocNodeForName != null) {
                                    varName.setJsDocNode(jsdocNodeForName);
                                }
                                String varNameString = ((Name) varName).getIdentifier();
                                if ("undefined".equals(varNameString)) {
                                    hasUndefinedBeenRedefined = true;
                                }
                                if (inUseStrictDirective) {
                                    if ("eval".equals(varNameString)
                                            || "arguments".equals(varNameString)) {
                                        reportError("msg.bad.id.strict", varNameString);
                                    }
                                }
                            }

                            // Non-standard extension: we support "catch (e if cond)
                            if (varName instanceof Name && matchToken(Token.IF, true)) {
                                guardPos = ts.tokenBeg;
                                catchCond = expr(false);
                            } else {
                                sawDefaultCatch = true;
                            }

                            if (mustMatchToken(Token.RP, "msg.bad.catchcond", true)) {
                                rp = ts.tokenBeg;
                            }
                            mustMatchToken(Token.LC, "msg.no.brace.catchblock", true);
                        }
                        break;
                    case Token.LC:
                        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                            matchToken(Token.LC, true);
                        } else {
                            reportError("msg.no.paren.catch");
                        }
                        break;
                    default:
                        reportError("msg.no.paren.catch");
                        break;
                }

                Scope catchScope = new Scope(catchPos);
                CatchClause catchNode = new CatchClause(catchPos);
                catchNode.setLineColumnNumber(catchLine, catchColumn);
                pushScope(catchScope);
                try {
                    // ES6: catch parameter prevents let/const redeclaration in catch block.
                    // Set the catch parameter name for redeclaration detection.
                    // We don't add it to the symbol table because that would trigger TDZ
                    // transformation in NodeTransformer.
                    if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                            && varName instanceof Name) {
                        String catchVarName = ((Name) varName).getIdentifier();
                        catchScope.setCatchParameterName(catchVarName);
                    }
                    statements(catchScope);
                } finally {
                    hasUndefinedBeenRedefined = previous;
                    popScope();
                }

                tryEnd = getNodeEnd(catchScope);
                catchNode.setVarName(varName);
                catchNode.setCatchCondition(catchCond);
                catchNode.setBody(catchScope);
                if (guardPos != -1) {
                    catchNode.setIfPosition(guardPos - catchPos);
                }
                catchNode.setParens(lp, rp);

                if (mustMatchToken(Token.RC, "msg.no.brace.after.body", true)) tryEnd = ts.tokenEnd;
                catchNode.setLength(tryEnd - catchPos);
                if (clauses == null) clauses = new ArrayList<>();
                clauses.add(catchNode);
            }
        } else if (peek != Token.FINALLY) {
            mustMatchToken(Token.FINALLY, "msg.try.no.catchfinally", true);
        }

        AstNode finallyBlock = null;
        if (matchToken(Token.FINALLY, true)) {
            finallyPos = ts.tokenBeg;
            finallyBlock = statement();
            tryEnd = getNodeEnd(finallyBlock);
        }

        pn.setLength(tryEnd - tryPos);
        pn.setTryBlock(tryBlock);
        pn.setCatchClauses(clauses);
        pn.setFinallyBlock(finallyBlock);
        if (finallyPos != -1) {
            pn.setFinallyPosition(finallyPos - tryPos);
        }
        pn.setLineColumnNumber(lineno, column);

        if (jsdocNode != null) {
            pn.setJsDocNode(jsdocNode);
        }

        return pn;
    }

    private ThrowStatement throwStatement() throws IOException {
        if (currentToken != Token.THROW) codeBug();
        consumeToken();
        int pos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        if (peekTokenOrEOL() == Token.EOL) {
            // ECMAScript does not allow new lines before throw expression,
            // see bug 256617
            reportError("msg.bad.throw.eol");
        }
        AstNode expr = expr(false);
        ThrowStatement pn = new ThrowStatement(pos, expr);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    // If we match a NAME, consume the token and return the statement
    // with that label.  If the name does not match an existing label,
    // reports an error.  Returns the labeled statement node, or null if
    // the peeked token was not a name.  Side effect:  sets scanner token
    // information for the label identifier (tokenBeg, tokenEnd, etc.)

    private LabeledStatement matchJumpLabelName() throws IOException {
        LabeledStatement label = null;

        if (peekTokenOrEOL() == Token.NAME) {
            consumeToken();
            if (labelSet != null) {
                label = labelSet.get(ts.getString());
            }
            if (label == null) {
                reportError("msg.undef.label");
            }
        }

        return label;
    }

    private BreakStatement breakStatement() throws IOException {
        if (currentToken != Token.BREAK) codeBug();
        consumeToken();
        int lineno = lineNumber(), pos = ts.tokenBeg, end = ts.tokenEnd, column = columnNumber();
        Name breakLabel = null;
        if (peekTokenOrEOL() == Token.NAME) {
            breakLabel = createNameNode();
            end = getNodeEnd(breakLabel);
        }

        // matchJumpLabelName only matches if there is one
        LabeledStatement labels = matchJumpLabelName();
        // always use first label as target
        Jump breakTarget = labels == null ? null : labels.getFirstLabel();

        if (breakTarget == null && breakLabel == null) {
            if (loopAndSwitchSet == null || loopAndSwitchSet.size() == 0) {
                reportError("msg.bad.break", pos, end - pos);
            } else {
                breakTarget = loopAndSwitchSet.get(loopAndSwitchSet.size() - 1);
            }
        }

        if (breakLabel != null) {
            breakLabel.setLineColumnNumber(lineNumber(), columnNumber());
        }

        BreakStatement pn = new BreakStatement(pos, end - pos);
        pn.setBreakLabel(breakLabel);
        // can be null if it's a bad break in error-recovery mode
        if (breakTarget != null) pn.setBreakTarget(breakTarget);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private ContinueStatement continueStatement() throws IOException {
        if (currentToken != Token.CONTINUE) codeBug();
        consumeToken();
        int lineno = lineNumber(), pos = ts.tokenBeg, end = ts.tokenEnd, column = columnNumber();
        Name label = null;
        if (peekTokenOrEOL() == Token.NAME) {
            label = createNameNode();
            end = getNodeEnd(label);
        }

        // matchJumpLabelName only matches if there is one
        LabeledStatement labels = matchJumpLabelName();
        Loop target = null;
        if (labels == null && label == null) {
            if (loopSet == null || loopSet.size() == 0) {
                reportError("msg.continue.outside");
            } else {
                target = loopSet.get(loopSet.size() - 1);
            }
        } else {
            if (labels == null || !(labels.getStatement() instanceof Loop)) {
                reportError("msg.continue.nonloop", pos, end - pos);
            }
            target = labels == null ? null : (Loop) labels.getStatement();
        }

        if (label != null) {
            label.setLineColumnNumber(lineNumber(), columnNumber());
        }

        ContinueStatement pn = new ContinueStatement(pos, end - pos);
        if (target != null) // can be null in error-recovery mode
        pn.setTarget(target);
        pn.setLabel(label);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private WithStatement withStatement() throws IOException {
        if (currentToken != Token.WITH) codeBug();
        consumeToken();

        Comment withComment = getAndResetJsDoc();

        int lineno = lineNumber(), column = columnNumber(), pos = ts.tokenBeg, lp = -1, rp = -1;
        if (mustMatchToken(Token.LP, "msg.no.paren.with", true)) lp = ts.tokenBeg;

        AstNode obj = expr(false);

        if (mustMatchToken(Token.RP, "msg.no.paren.after.with", true)) rp = ts.tokenBeg;

        WithStatement pn = new WithStatement(pos);

        boolean previous = hasUndefinedBeenRedefined;
        try {
            hasUndefinedBeenRedefined = true;
            AstNode body = getNextStatementAfterInlineComments(pn);

            pn.setLength(getNodeEnd(body) - pos);
            pn.setJsDocNode(withComment);
            pn.setExpression(obj);
            pn.setStatement(body);
            pn.setParens(lp, rp);
            pn.setLineColumnNumber(lineno, column);
        } finally {
            hasUndefinedBeenRedefined = previous;
        }

        return pn;
    }

    /**
     * Parse an import declaration.
     *
     * <pre>
     * ImportDeclaration:
     *   import ImportClause FromClause ;
     *   import ModuleSpecifier ;
     * ImportClause:
     *   ImportedDefaultBinding
     *   NameSpaceImport
     *   NamedImports
     *   ImportedDefaultBinding , NameSpaceImport
     *   ImportedDefaultBinding , NamedImports
     * NameSpaceImport:
     *   * as ImportedBinding
     * NamedImports:
     *   { }
     *   { ImportsList }
     *   { ImportsList , }
     * FromClause:
     *   from ModuleSpecifier
     * </pre>
     */
    private ImportDeclaration parseImport() throws IOException {
        if (currentToken != Token.IMPORT) codeBug();
        consumeToken();

        int pos = ts.tokenBeg;
        int lineno = lineNumber();
        int column = columnNumber();

        ImportDeclaration pn = new ImportDeclaration(pos);
        pn.setLineColumnNumber(lineno, column);

        int tt = peekToken();

        // import "module" (side-effect only import)
        if (tt == Token.STRING) {
            consumeToken();
            pn.setModuleSpecifier(ts.getString());
            pn.setLength(ts.tokenEnd - pos);
            autoInsertSemicolon(pn);
            return pn;
        }

        // Parse import bindings
        boolean hasDefault = false;

        // import x from "module" (default import)
        if (tt == Token.NAME) {
            Name defaultName = createNameNode();
            consumeToken();
            pn.setDefaultImport(defaultName);
            // Note: Don't call defineSymbol for import bindings.
            // Import bindings are handled by ModuleScope at runtime, not as regular variables.
            hasDefault = true;
            tt = peekToken();

            // Check for comma (default + named/namespace)
            if (tt == Token.COMMA) {
                consumeToken();
                tt = peekToken();
            } else if (tt != Token.NAME || !"from".equals(ts.getString())) {
                reportError("msg.import.expected.from");
            }
        }

        // import * as ns from "module" (namespace import)
        if (tt == Token.MUL) {
            consumeToken();
            if (!matchToken(Token.NAME, true) || !"as".equals(ts.getString())) {
                reportError("msg.import.expected.as");
            }
            if (!matchToken(Token.NAME, true)) {
                reportError("msg.import.expected.binding");
            }
            Name nsName = createNameNode(true, Token.NAME);
            pn.setNamespaceImport(nsName);
            // Note: Don't call defineSymbol for import bindings.
        }
        // import { a, b as c } from "module" (named imports)
        else if (tt == Token.LC) {
            consumeToken();
            parseImportSpecifiers(pn);
        }
        // If no namespace or named imports but we had a default, we're done with bindings
        else if (!hasDefault) {
            reportError("msg.import.expected.clause");
        }

        // Parse "from"
        if (!matchToken(Token.NAME, true) || !"from".equals(ts.getString())) {
            reportError("msg.import.expected.from");
        }

        // Parse module specifier
        if (!matchToken(Token.STRING, true)) {
            reportError("msg.import.expected.module");
        }
        pn.setModuleSpecifier(ts.getString());

        pn.setLength(ts.tokenEnd - pos);
        autoInsertSemicolon(pn);
        return pn;
    }

    /**
     * Parse import specifiers: { a, b as c, ... }
     *
     * <p>Called after the opening brace has been consumed.
     */
    private void parseImportSpecifiers(ImportDeclaration importDecl) throws IOException {
        while (true) {
            int tt = peekToken();
            if (tt == Token.RC) {
                consumeToken();
                break;
            }

            // Expect an identifier (imported name)
            if (tt != Token.NAME
                    && !TokenStream.isKeyword(
                            ts.getString(), compilerEnv.getLanguageVersion(), false)) {
                reportError("msg.import.expected.binding");
                break;
            }

            int specPos = ts.tokenBeg;
            String importedName = ts.getString();
            consumeToken();
            Name importedNameNode = new Name(specPos, importedName);
            importedNameNode.setLineColumnNumber(lineNumber(), columnNumber());

            ImportSpecifier spec = new ImportSpecifier(specPos);
            spec.setImportedName(importedNameNode);

            // Check for "as localName"
            if (matchToken(Token.NAME, true) && "as".equals(ts.getString())) {
                if (!matchToken(Token.NAME, true)) {
                    reportError("msg.import.expected.binding");
                }
                Name localNameNode = createNameNode(true, Token.NAME);
                spec.setLocalName(localNameNode);
                // Note: Don't call defineSymbol for import bindings.
            } else {
                // Use imported name as local name
                Name localNameNode = new Name(specPos, importedName);
                localNameNode.setLineColumnNumber(lineNumber(), columnNumber());
                spec.setLocalName(localNameNode);
                // Note: Don't call defineSymbol for import bindings.
            }

            spec.setLength(ts.tokenEnd - specPos);
            importDecl.addNamedImport(spec);

            // Expect comma or closing brace
            tt = peekToken();
            if (tt == Token.COMMA) {
                consumeToken();
            } else if (tt != Token.RC) {
                reportError("msg.import.expected.comma.or.brace");
                break;
            }
        }
    }

    /**
     * Parse an export declaration.
     *
     * <pre>
     * ExportDeclaration:
     *   export ExportFromClause FromClause ;
     *   export NamedExports ;
     *   export VariableStatement
     *   export Declaration
     *   export default HoistableDeclaration
     *   export default ClassDeclaration
     *   export default AssignmentExpression ;
     * ExportFromClause:
     *   *
     *   * as IdentifierName
     *   NamedExports
     * NamedExports:
     *   { }
     *   { ExportsList }
     *   { ExportsList , }
     * </pre>
     */
    private ExportDeclaration parseExport() throws IOException {
        if (currentToken != Token.EXPORT) codeBug();
        consumeToken();

        int pos = ts.tokenBeg;
        int lineno = lineNumber();
        int column = columnNumber();

        ExportDeclaration pn = new ExportDeclaration(pos);
        pn.setLineColumnNumber(lineno, column);

        int tt = peekToken();

        // export default ...
        if (tt == Token.DEFAULT) {
            consumeToken();
            pn.setDefault(true);
            return parseExportDefault(pn, pos);
        }

        // export * from "module" or export * as ns from "module"
        if (tt == Token.MUL) {
            consumeToken();
            pn.setStarExport(true);

            // Peek to check for "as" without consuming
            tt = peekToken();
            if (tt == Token.NAME && "as".equals(ts.getString())) {
                consumeToken(); // consume "as"
                if (!matchToken(Token.NAME, true)) {
                    reportError("msg.export.expected.binding");
                }
                Name aliasName = createNameNode(true, Token.NAME);
                pn.setStarExportAlias(aliasName);
            }

            // Require "from"
            if (!matchToken(Token.NAME, true) || !"from".equals(ts.getString())) {
                reportError("msg.export.expected.from");
            }

            // Parse module specifier
            if (!matchToken(Token.STRING, true)) {
                reportError("msg.export.expected.module");
            }
            pn.setFromModuleSpecifier(ts.getString());

            pn.setLength(ts.tokenEnd - pos);
            autoInsertSemicolon(pn);
            return pn;
        }

        // export { a, b as c } or export { a, b } from "module"
        if (tt == Token.LC) {
            consumeToken();
            parseExportSpecifiers(pn);

            // Check for re-export: from "module"
            if (matchToken(Token.NAME, true) && "from".equals(ts.getString())) {
                if (!matchToken(Token.STRING, true)) {
                    reportError("msg.export.expected.module");
                }
                pn.setFromModuleSpecifier(ts.getString());
            }

            pn.setLength(ts.tokenEnd - pos);
            autoInsertSemicolon(pn);
            return pn;
        }

        // export function ...
        if (tt == Token.FUNCTION) {
            consumeToken();
            FunctionNode fn = function(FunctionNode.FUNCTION_STATEMENT);
            pn.setDeclaration(fn);
            pn.setLength(getNodeEnd(fn) - pos);
            return pn;
        }

        // export class ...
        if (tt == Token.CLASS) {
            consumeToken();
            ClassNode cn = classDeclaration(ClassNode.CLASS_STATEMENT);
            pn.setDeclaration(cn);
            pn.setLength(getNodeEnd(cn) - pos);
            return pn;
        }

        // export var/let/const ...
        if (tt == Token.VAR || tt == Token.LET || tt == Token.CONST) {
            consumeToken();
            VariableDeclaration vars = variables(currentToken, ts.tokenBeg, true);
            pn.setDeclaration(vars);
            pn.setLength(ts.tokenEnd - pos);
            autoInsertSemicolon(pn);
            return pn;
        }

        reportError("msg.export.expected.declaration");
        return pn;
    }

    /**
     * Parse export default declaration.
     *
     * <p>Called after "export default" has been consumed.
     */
    private ExportDeclaration parseExportDefault(ExportDeclaration pn, int pos) throws IOException {
        int tt = peekToken();

        // export default function ...
        if (tt == Token.FUNCTION) {
            consumeToken();
            // Parse initially as expression (allows anonymous functions)
            FunctionNode fn = function(FunctionNode.FUNCTION_EXPRESSION);
            // Treat function declarations (named or anonymous) as hoistable
            // For anonymous default exports, use *default* as the binding name
            // so they get hoisted and bound during module instantiation
            if (fn.getFunctionName() != null) {
                fn.setFunctionType(FunctionNode.FUNCTION_STATEMENT);
            } else {
                // Anonymous default export: give it the internal name *default* for hoisting
                // The display name will be set to "default" later via function name inference
                Name starDefault = new Name(fn.getPosition(), "*default*");
                fn.setFunctionName(starDefault);
                fn.setFunctionType(FunctionNode.FUNCTION_STATEMENT);
            }
            pn.setDeclaration(fn);
            pn.setLength(getNodeEnd(fn) - pos);
            return pn;
        }

        // export default class ...
        if (tt == Token.CLASS) {
            consumeToken();
            ClassNode cn = classDeclaration(ClassNode.CLASS_EXPRESSION);
            pn.setDeclaration(cn);
            pn.setLength(getNodeEnd(cn) - pos);
            return pn;
        }

        // export default <expression>
        AstNode expr = assignExpr();
        pn.setDefaultExpression(expr);
        pn.setLength(ts.tokenEnd - pos);
        autoInsertSemicolon(pn);
        return pn;
    }

    /**
     * Parse export specifiers: { a, b as c, ... }
     *
     * <p>Called after the opening brace has been consumed.
     */
    private void parseExportSpecifiers(ExportDeclaration exportDecl) throws IOException {
        while (true) {
            int tt = peekToken();
            if (tt == Token.RC) {
                consumeToken();
                break;
            }

            // Expect an identifier (local name)
            if (tt != Token.NAME
                    && !TokenStream.isKeyword(
                            ts.getString(), compilerEnv.getLanguageVersion(), false)) {
                reportError("msg.export.expected.binding");
                break;
            }

            int specPos = ts.tokenBeg;
            String localName = ts.getString();
            consumeToken();
            Name localNameNode = new Name(specPos, localName);
            localNameNode.setLineColumnNumber(lineNumber(), columnNumber());

            ExportSpecifier spec = new ExportSpecifier(specPos);
            spec.setLocalName(localNameNode);

            // Check for "as exportedName"
            // The exported name can be "default" or any IdentifierName (including keywords)
            if (matchToken(Token.NAME, true) && "as".equals(ts.getString())) {
                int nextTt = peekToken();
                // Allow "default" keyword as exported name (export { x as default })
                if (nextTt == Token.DEFAULT) {
                    consumeToken();
                    Name exportedNameNode = new Name(ts.tokenBeg, "default");
                    exportedNameNode.setLineColumnNumber(lineNumber(), columnNumber());
                    spec.setExportedName(exportedNameNode);
                } else if (matchToken(Token.NAME, true)
                        || TokenStream.isKeyword(
                                ts.getString(), compilerEnv.getLanguageVersion(), parsingModule)) {
                    // Allow any identifier or keyword as exported name
                    Name exportedNameNode = createNameNode(true, Token.NAME);
                    spec.setExportedName(exportedNameNode);
                } else {
                    reportError("msg.export.expected.binding");
                }
            } else {
                // Use local name as exported name
                Name exportedNameNode = new Name(specPos, localName);
                exportedNameNode.setLineColumnNumber(lineNumber(), columnNumber());
                spec.setExportedName(exportedNameNode);
            }

            spec.setLength(ts.tokenEnd - specPos);
            exportDecl.addNamedExport(spec);

            // Expect comma or closing brace
            tt = peekToken();
            if (tt == Token.COMMA) {
                consumeToken();
            } else if (tt != Token.RC) {
                reportError("msg.export.expected.comma.or.brace");
                break;
            }
        }
    }

    /**
     * Handle LET which can be either a declaration keyword or an identifier in non-strict mode. Per
     * ES6 spec: - 'let [' is always a declaration (lookahead restriction applies even across
     * newlines) - 'let {' with newline: ASI applies, 'let' is identifier, '{}' is a block - 'let
     * <identifier>' with newline in single-statement context: ASI applies - 'let <identifier>' with
     * newline in block context: it's a declaration - 'let' followed by something that can't start a
     * binding with newline: ASI applies
     */
    private AstNode letStatementOrIdentifier() throws IOException {
        if (currentToken != Token.LET) codeBug();

        // In strict mode or pre-ES6, 'let' is always a keyword
        if (inUseStrictDirective || compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
            return letStatement();
        }

        // Consume the 'let' token first so we can peek at what follows
        consumeToken();
        int letPos = ts.tokenBeg;
        int letLineno = lineNumber();
        int letColumn = columnNumber();

        // Check what follows 'let' and if there's a line break
        int nextToken = peekTokenOrEOL();
        boolean hasLineBreak = (nextToken == Token.EOL);

        // Get the actual next token (ignoring line breaks)
        int actualNextToken = peekToken();

        // 'let [' is always a declaration - the lookahead restriction applies even across newlines
        if (actualNextToken == Token.LB) {
            return letStatementAfterConsume(letPos, letLineno, letColumn);
        }

        // 'let {' with newline: ASI applies, 'let' is identifier
        if (hasLineBreak && actualNextToken == Token.LC) {
            return letAsIdentifierExpression(letPos, letLineno, letColumn);
        }

        // Check if next token can start a binding (identifier, {, yield, await)
        boolean canStartBinding =
                actualNextToken == Token.NAME
                        || actualNextToken == Token.LC
                        || actualNextToken == Token.YIELD
                        || actualNextToken == Token.LET;

        if (canStartBinding) {
            // In single-statement context with line break: ASI applies, 'let' is identifier
            if (hasLineBreak && inSingleStatementContext) {
                return letAsIdentifierExpression(letPos, letLineno, letColumn);
            }
            // Otherwise, it's a let declaration
            return letStatementAfterConsume(letPos, letLineno, letColumn);
        }

        // If next token can't start a binding, treat 'let' as an identifier
        // This handles cases like: let = 1; let(); let.foo; etc.
        return letAsIdentifierExpression(letPos, letLineno, letColumn);
    }

    /**
     * Parse 'let' as an identifier in an expression statement. Used in non-strict mode when 'let'
     * is not followed by something that can start a binding. Called after 'let' token has been
     * consumed.
     */
    private AstNode letAsIdentifierExpression(int pos, int lineno, int column) throws IOException {
        // Save token info so createNameNode can use it
        saveNameTokenData(pos, "let", lineno, column);
        AstNode letName = createNameNode(true, Token.NAME);

        // Continue parsing member expressions (property access, calls, etc.)
        AstNode expr = memberExprTail(false, letName);

        // Continue parsing for assignment operators
        expr = assignExprTail(expr);

        // Check for comma expressions
        while (matchToken(Token.COMMA, true)) {
            int opPos = ts.tokenBeg;
            expr = new InfixExpression(Token.COMMA, expr, assignExpr(), opPos);
        }

        AstNode pn = new ExpressionStatement(expr, !insideFunctionBody());
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    /** Continue parsing let statement/expression after 'let' token has been consumed. */
    private AstNode letStatementAfterConsume(int pos, int lineno, int column) throws IOException {
        AstNode pn;
        if (peekToken() == Token.LP) {
            pn = let(true, pos);
        } else {
            pn = variables(Token.LET, pos, true);
        }
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private AstNode letStatement() throws IOException {
        if (currentToken != Token.LET) codeBug();
        consumeToken();
        int lineno = lineNumber(), pos = ts.tokenBeg, column = columnNumber();
        AstNode pn;
        if (peekToken() == Token.LP) {
            pn = let(true, pos);
        } else {
            pn = variables(Token.LET, pos, true); // else, e.g.: let x=6, y=7;
        }
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    /**
     * Returns whether or not the bits in the mask have changed to all set.
     *
     * @param before bits before change
     * @param after bits after change
     * @param mask mask for bits
     * @return {@code true} if all the bits in the mask are set in "after" but not in "before"
     */
    private static final boolean nowAllSet(int before, int after, int mask) {
        return ((before & mask) != mask) && ((after & mask) == mask);
    }

    private AstNode returnOrYield(int tt, boolean exprContext) throws IOException {
        if (!insideFunctionBody()) {
            reportError(tt == Token.RETURN ? "msg.bad.return" : "msg.bad.yield");
        }
        consumeToken();
        int lineno = lineNumber(), column = columnNumber(), pos = ts.tokenBeg, end = ts.tokenEnd;

        boolean yieldStar = false;
        // Per ES6 spec, no line terminator is allowed between yield and *
        // Use peekTokenOrEOL to detect newlines
        if ((tt == Token.YIELD)
                && (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6)
                && (peekTokenOrEOL() == Token.MUL)) {
            yieldStar = true;
            consumeToken();
        }

        AstNode e = null;
        // This is ugly, but we don't want to require a semicolon.
        // For yield* expressions, the expression is required and can start on a new line,
        // so we use peekToken() instead of peekTokenOrEOL().
        int nextToken = yieldStar ? peekToken() : peekTokenOrEOL();
        switch (nextToken) {
            case Token.SEMI:
            case Token.RC:
            case Token.RB:
            case Token.RP:
            case Token.EOF:
            case Token.EOL:
            case Token.ERROR:
            case Token.COMMA:
                // Per ES spec, yield takes an AssignmentExpression, not an Expression.
                // So comma terminates the yield operand (allowing "yield, yield" in generators).
                // For yield*, an expression is required, so report error if we hit these.
                if (yieldStar) {
                    reportError("msg.syntax");
                }
                break;
            case Token.COLON:
                // Colon terminates yield when used in conditional expression (e.g., "a ? yield :
                // b")
                if (yieldStar) {
                    reportError("msg.syntax");
                }
                break;
            case Token.YIELD:
                if (compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
                    // Take extra care to preserve language compatibility
                    break;
                }
            // fallthrough
            default:
                e = assignExpr();
                end = getNodeEnd(e);
        }

        int before = endFlags;
        AstNode ret;

        if (tt == Token.RETURN) {
            endFlags |= e == null ? Node.END_RETURNS : Node.END_RETURNS_VALUE;
            ret = new ReturnStatement(pos, end - pos, e);

            // see if we need a strict mode warning
            if (nowAllSet(before, endFlags, Node.END_RETURNS | Node.END_RETURNS_VALUE))
                addStrictWarning("msg.return.inconsistent", "", pos, end - pos);
        } else {
            if (!insideFunctionBody()) reportError("msg.bad.yield");
            endFlags |= Node.END_YIELDS;
            ret = new Yield(pos, end - pos, e, yieldStar);
            setRequiresActivation();
            setIsGenerator();
            if (!exprContext) {
                ret.setLineColumnNumber(lineno, column);
                ret = new ExpressionStatement(ret);
            }
        }

        // see if we are mixing yields and value returns.
        if (insideFunctionBody()
                && nowAllSet(before, endFlags, Node.END_YIELDS | Node.END_RETURNS_VALUE)) {
            FunctionNode fn = (FunctionNode) currentScriptOrFn;
            if (!fn.isES6Generator()) {
                Name name = ((FunctionNode) currentScriptOrFn).getFunctionName();
                if (name == null || name.length() == 0) {
                    addError("msg.anon.generator.returns", "");
                } else {
                    addError("msg.generator.returns", name.getIdentifier());
                }
            }
        }

        ret.setLineColumnNumber(lineno, column);
        return ret;
    }

    /**
     * Parse an await expression. Called when Token.AWAIT is seen inside an async function.
     *
     * <pre>
     * AwaitExpression:
     *     await UnaryExpression
     * </pre>
     */
    private AstNode awaitExpr() throws IOException {
        if (!insideFunctionBody()) {
            reportError("msg.bad.await");
        }
        consumeToken();
        int lineno = lineNumber(), column = columnNumber(), pos = ts.tokenBeg, end = ts.tokenEnd;

        // await requires an operand
        AstNode e = unaryExpr();
        end = getNodeEnd(e);

        Await ret = new Await(pos, end - pos, e);
        ret.setLineColumnNumber(lineno, column);
        setRequiresActivation();
        return ret;
    }

    private AstNode block() throws IOException {
        if (currentToken != Token.LC) codeBug();
        consumeToken();
        int pos = ts.tokenBeg;
        Scope block = new Scope(pos);
        block.setLineColumnNumber(lineNumber(), columnNumber());
        pushScope(block);
        // Per ES6 sec-static-semantics-containsundefinedcontinuetarget:
        // BlockStatement clears the label set for continue target validation.
        // This ensures "continue label" inside a block cannot target a label
        // that is attached to the block (not directly to an iteration statement).
        // We achieve this by clearing currentLabel so that loops inside this
        // block don't get associated with labels outside the block.
        LabeledStatement savedLabel = currentLabel;
        currentLabel = null;
        try {
            statements(block);
            mustMatchToken(Token.RC, "msg.no.brace.block", true);
            block.setLength(ts.tokenEnd - pos);
            return block;
        } finally {
            currentLabel = savedLabel;
            popScope();
        }
    }

    private AstNode defaultXmlNamespace() throws IOException {
        if (currentToken != Token.DEFAULT) codeBug();
        consumeToken();
        mustHaveXML();
        setRequiresActivation();
        int lineno = lineNumber(), column = columnNumber(), pos = ts.tokenBeg;

        if (!(matchToken(Token.NAME, true) && "xml".equals(ts.getString()))) {
            reportError("msg.bad.namespace");
        }
        if (!(matchToken(Token.NAME, true) && "namespace".equals(ts.getString()))) {
            reportError("msg.bad.namespace");
        }
        if (!matchToken(Token.ASSIGN, true)) {
            reportError("msg.bad.namespace");
        }

        AstNode e = expr(false);
        UnaryExpression dxmln = new UnaryExpression(pos, getNodeEnd(e) - pos);
        dxmln.setOperator(Token.DEFAULTNAMESPACE);
        dxmln.setOperand(e);
        dxmln.setLineColumnNumber(lineno, column);

        ExpressionStatement es = new ExpressionStatement(dxmln, true);
        return es;
    }

    private void recordLabel(Label label, LabeledStatement bundle) throws IOException {
        // current token should be colon that primaryExpr left untouched
        if (peekToken() != Token.COLON) codeBug();
        consumeToken();
        String name = label.getName();
        if (labelSet == null) {
            labelSet = new HashMap<>();
        } else {
            LabeledStatement ls = labelSet.get(name);
            if (ls != null) {
                if (compilerEnv.isIdeMode()) {
                    Label dup = ls.getLabelByName(name);
                    reportError("msg.dup.label", dup.getAbsolutePosition(), dup.getLength());
                }
                reportError("msg.dup.label", label.getPosition(), label.getLength());
            }
        }
        bundle.addLabel(label);
        labelSet.put(name, bundle);
    }

    /**
     * Found a name in a statement context. If it's a label, we gather up any following labels and
     * the next non-label statement into a {@link LabeledStatement} "bundle" and return that.
     * Otherwise we parse an expression and return it wrapped in an {@link ExpressionStatement}.
     */
    private AstNode nameOrLabel() throws IOException {
        if (currentToken != Token.NAME) throw codeBug();
        int pos = ts.tokenBeg;

        // set check for label and call down to primaryExpr
        currentFlaggedToken |= TI_CHECK_LABEL;
        AstNode expr = expr(false);

        if (expr.getType() != Token.LABEL) {
            AstNode n = new ExpressionStatement(expr, !insideFunctionBody());
            n.setLineColumnNumber(expr.getLineno(), expr.getColumn());
            return n;
        }

        LabeledStatement bundle = new LabeledStatement(pos);
        recordLabel((Label) expr, bundle);
        bundle.setLineColumnNumber(expr.getLineno(), expr.getColumn());
        // look for more labels
        AstNode stmt = null;
        while (peekToken() == Token.NAME) {
            currentFlaggedToken |= TI_CHECK_LABEL;
            expr = expr(false);
            if (expr.getType() != Token.LABEL) {
                stmt = new ExpressionStatement(expr, !insideFunctionBody());
                autoInsertSemicolon(stmt);
                break;
            }
            recordLabel((Label) expr, bundle);
        }

        // no more labels; now parse the labeled statement
        ++nestingOfStatement;
        try {
            currentLabel = bundle;
            if (stmt == null) {
                stmt = statementHelper();
                // Lexical declarations cannot be the body of a labeled statement (ES6+)
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                        && stmt instanceof VariableDeclaration) {
                    VariableDeclaration vd = (VariableDeclaration) stmt;
                    if (vd.getType() == Token.LET || vd.getType() == Token.CONST) {
                        reportError("msg.lexical.decl.not.in.block");
                    }
                }
                // Generator and async declarations cannot be labeled (ES6+)
                // Function declarations cannot be labeled in strict mode (ES6+)
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                        && stmt instanceof FunctionNode) {
                    FunctionNode fn = (FunctionNode) stmt;
                    if (fn.isES6Generator()) {
                        reportError("msg.generator.decl.not.in.block");
                    } else if (fn.isAsync()) {
                        reportError("msg.async.decl.not.in.block");
                    } else if (inUseStrictDirective) {
                        reportError("msg.func.decl.labeled.strict");
                    }
                }
                // Class declarations cannot be labeled (ES6+)
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                        && stmt instanceof ClassNode) {
                    reportError("msg.class.decl.not.in.block");
                }
                int ntt = peekToken();
                if (ntt == Token.COMMENT
                        && stmt.getLineno()
                                == scannedComments.get(scannedComments.size() - 1).getLineno()) {
                    stmt.setInlineComment(scannedComments.get(scannedComments.size() - 1));
                    consumeToken();
                }
            }
        } finally {
            --nestingOfStatement;
            currentLabel = null;
            // remove the labels for this statement from the global set
            for (Label lb : bundle.getLabels()) {
                labelSet.remove(lb.getName());
            }
        }

        // If stmt has parent assigned its position already is relative
        // (See bug #710225)
        bundle.setLength(stmt.getParent() == null ? getNodeEnd(stmt) - pos : getNodeEnd(stmt));
        bundle.setStatement(stmt);
        return bundle;
    }

    /**
     * Handle 'await' in a statement context when not inside an async function. 'await' can be used
     * as an identifier and label in non-module, non-async-function code.
     */
    private AstNode awaitNameOrLabel() throws IOException {
        if (currentToken != Token.AWAIT) throw codeBug();
        int pos = ts.tokenBeg;
        int lineno = lineNumber();
        int column = columnNumber();

        // Convert AWAIT to a NAME token for the label check
        saveNameTokenData(pos, "await", lineno, column);

        // Check if followed by colon (label syntax)
        consumeToken();
        if (peekToken() == Token.COLON) {
            // It's a label
            Label label = new Label(pos, ts.tokenEnd - pos);
            label.setName("await");
            label.setLineColumnNumber(lineno, column);

            // Don't consume colon - recordLabel expects it
            LabeledStatement bundle = new LabeledStatement(pos);
            recordLabel(label, bundle); // This consumes the colon
            bundle.setLineColumnNumber(lineno, column);

            // look for more labels
            AstNode stmt = null;
            while (peekToken() == Token.NAME) {
                currentFlaggedToken |= TI_CHECK_LABEL;
                AstNode expr = expr(false);
                if (expr.getType() != Token.LABEL) {
                    stmt = new ExpressionStatement(expr, !insideFunctionBody());
                    autoInsertSemicolon(stmt);
                    break;
                }
                recordLabel((Label) expr, bundle);
            }

            // no more labels; now parse the labeled statement
            ++nestingOfStatement;
            try {
                currentLabel = bundle;
                if (stmt == null) {
                    stmt = statementHelper();
                }
            } finally {
                --nestingOfStatement;
                currentLabel = null;
                for (Label lb : bundle.getLabels()) {
                    labelSet.remove(lb.getName());
                }
            }

            bundle.setLength(stmt.getParent() == null ? getNodeEnd(stmt) - pos : getNodeEnd(stmt));
            bundle.setStatement(stmt);
            return bundle;
        }

        // Not a label - parse as expression
        // We've already consumed 'await', so create name node and continue
        AstNode awaitName = createNameNode(true, Token.NAME);
        AstNode expr = memberExprTail(true, awaitName);
        expr = assignExprTail(expr);
        // Check for comma expression continuation
        while (matchToken(Token.COMMA, true)) {
            int opPos = ts.tokenBeg;
            expr = new InfixExpression(Token.COMMA, expr, assignExpr(), opPos);
        }
        AstNode n = new ExpressionStatement(expr, !insideFunctionBody());
        n.setLineColumnNumber(lineno, column);
        return n;
    }

    /**
     * Parse a 'var' or 'const' statement, or a 'var' init list in a for statement.
     *
     * @param declType A token value: either VAR, CONST, or LET depending on context.
     * @param pos the position where the node should start. It's sometimes the var/const/let
     *     keyword, and other times the beginning of the first token in the first variable
     *     declaration.
     * @return the parsed variable list
     */
    private VariableDeclaration variables(int declType, int pos, boolean isStatement)
            throws IOException {
        int end;
        VariableDeclaration pn = new VariableDeclaration(pos);
        pn.setType(declType);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        Comment varjsdocNode = getAndResetJsDoc();
        if (varjsdocNode != null) {
            pn.setJsDocNode(varjsdocNode);
        }
        // Example:
        // var foo = {a: 1, b: 2}, bar = [3, 4];
        // var {b: s2, a: s1} = foo, x = 6, y, [s3, s4] = bar;
        for (; ; ) {
            AstNode destructuring = null;
            Name name = null;
            int tt = peekToken(), kidPos = ts.tokenBeg;
            end = ts.tokenEnd;

            if (tt == Token.LB || tt == Token.LC) {
                // Destructuring assignment, e.g., var [a,b] = ...

                // TODO: support default values inside destructured assignment
                // eg: for (let { x = 3 } = {}) ...
                destructuring = destructuringPrimaryExpr();
                end = getNodeEnd(destructuring);

                if (!(destructuring instanceof DestructuringForm))
                    reportError("msg.bad.assign.left", kidPos, end - kidPos);
                markDestructuring(destructuring);
            } else {
                // Simple variable name
                // In non-strict mode, 'let' can be used as an identifier
                if (tt == Token.UNDEFINED || isIdentifierToken(tt)) {
                    consumeToken();
                } else {
                    mustMatchIdentifier("msg.bad.var", true);
                }
                name = createNameNode();
                name.setLineColumnNumber(lineNumber(), columnNumber());
                String id = ts.getString();
                // ES6: 'let' cannot be used as a bound name in let/const declarations
                if ((declType == Token.LET || declType == Token.CONST) && "let".equals(id)) {
                    reportError("msg.let.not.valid.id");
                }
                if (inUseStrictDirective) {
                    if ("eval".equals(id) || "arguments".equals(id)) {
                        reportError("msg.bad.id.strict", id);
                    }
                }
                defineSymbol(declType, id, inForInit);
            }

            int lineno = lineNumber(), column = columnNumber();

            Comment jsdocNode = getAndResetJsDoc();

            AstNode init = null;
            if (matchToken(Token.ASSIGN, true)) {
                init = assignExpr();
                end = getNodeEnd(init);
            } else if (!inForInit) {
                // If no initializer, the next token must be ',', ';', EOL (for ASI),
                // or end of block/input.
                // This catches syntax errors like: let x 0; (where 0 is unexpected)
                int nextTT = peekTokenOrEOL();
                if (nextTT != Token.COMMA
                        && nextTT != Token.SEMI
                        && nextTT != Token.EOL
                        && nextTT != Token.EOF
                        && nextTT != Token.ERROR
                        && nextTT != Token.RC) { // closing brace for block
                    reportError("msg.syntax");
                }
            }

            VariableInitializer vi = new VariableInitializer(kidPos, end - kidPos);
            if (destructuring != null) {
                if (init == null && !inForInit) {
                    reportError("msg.destruct.assign.no.init");
                }
                vi.setTarget(destructuring);
            } else {
                vi.setTarget(name);
                // const declarations must have an initializer (except in for-in/for-of)
                // Only enforce in ES6+ mode; older versions allow non-standard behavior.
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                        && declType == Token.CONST
                        && init == null
                        && !inForInit) {
                    reportError("msg.const.no.init");
                }
            }
            vi.setInitializer(init);
            vi.setType(declType);
            vi.setJsDocNode(jsdocNode);
            vi.setLineColumnNumber(lineno, column);
            pn.addVariable(vi);

            if (!matchToken(Token.COMMA, true)) break;
        }
        pn.setLength(end - pos);
        pn.setIsStatement(isStatement);
        return pn;
    }

    // have to pass in 'let' kwd position to compute kid offsets properly
    private AstNode let(boolean isStatement, int pos) throws IOException {
        LetNode pn = new LetNode(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        if (mustMatchToken(Token.LP, "msg.no.paren.after.let", true)) pn.setLp(ts.tokenBeg - pos);
        pushScope(pn);
        try {
            VariableDeclaration vars = variables(Token.LET, ts.tokenBeg, isStatement);
            pn.setVariables(vars);
            if (mustMatchToken(Token.RP, "msg.no.paren.let", true)) {
                pn.setRp(ts.tokenBeg - pos);
            }
            if (isStatement && peekToken() == Token.LC) {
                // let statement
                consumeToken();
                int beg = ts.tokenBeg; // position stmt at LC
                AstNode stmt = statements();
                mustMatchToken(Token.RC, "msg.no.curly.let", true);
                stmt.setLength(ts.tokenEnd - beg);
                pn.setLength(ts.tokenEnd - pos);
                pn.setBody(stmt);
                pn.setType(Token.LET);
            } else {
                // let expression
                AstNode expr = expr(false);
                pn.setLength(getNodeEnd(expr) - pos);
                pn.setBody(expr);
                if (isStatement) {
                    // let expression in statement context
                    ExpressionStatement es = new ExpressionStatement(pn, !insideFunctionBody());
                    es.setLineColumnNumber(pn.getLineno(), pn.getColumn());
                    return es;
                }
            }
        } finally {
            popScope();
        }
        return pn;
    }

    /**
     * @return true if Annex B hoisting was skipped due to let/const conflict
     */
    boolean defineSymbol(int declType, String name) {
        return defineSymbol(declType, name, false, false);
    }

    boolean defineSymbol(int declType, String name, boolean ignoreNotInBlock) {
        return defineSymbol(declType, name, ignoreNotInBlock, false);
    }

    /**
     * @param isGenerator true if this is a generator function/method declaration
     * @return true if Annex B hoisting was skipped due to let/const conflict
     */
    boolean defineSymbol(int declType, String name, boolean ignoreNotInBlock, boolean isGenerator) {
        if (name == null) {
            if (compilerEnv.isIdeMode()) { // be robust in IDE-mode
                return false;
            }
            codeBug();
        } else if ("undefined".equals(name)) {
            hasUndefinedBeenRedefined = true;
        }
        Scope definingScope = currentScope.getDefiningScope(name);
        Symbol symbol = definingScope != null ? definingScope.getSymbol(name) : null;
        int symDeclType = symbol != null ? symbol.getDeclType() : -1;
        // Check for let/const redeclaration errors:
        // 1. const can't redeclare const in SAME scope (but can shadow in nested scope)
        // 2. let can't redeclare let in same scope
        // 3. let/const can't redeclare var that was declared in the same block (even though var
        //    hoists, the conflict is detected at the block level per ES6 spec)
        boolean varInSameBlock =
                (declType == Token.LET || declType == Token.CONST)
                        && currentScope.hasVarNameInBlock(name);
        // ES6: let/const can't redeclare catch parameter in the same catch block.
        // Per ES6 13.15.1 Static Semantics: Early Errors, if any element of the BoundNames
        // of CatchParameter also occurs in the LexicallyDeclaredNames of Block, it's an error.
        // Function declarations directly in the catch block (not inside an if/for/etc.) are
        // also lexically scoped and conflict with the catch parameter per ES6 13.15.1.
        // However, Annex B.3.2 allows function declarations inside if/else as the statement
        // body, which have different scoping rules (indicated by inSingleStatementContext).
        boolean isFunctionInStatementPosition =
                declType == Token.FUNCTION && inSingleStatementContext;
        boolean catchParamRedecl =
                (declType == Token.LET
                                || declType == Token.CONST
                                || (declType == Token.FUNCTION && !isFunctionInStatementPosition))
                        && currentScope.isCatchParameterName(name);
        if (catchParamRedecl) {
            addError("msg.let.redecl", name);
            return false;
        }
        // ES6: Check for let/const conflicting with prior function/generator declaration in the
        // same block. In ES6, function/generator declarations in blocks are part of
        // LexicallyDeclaredNames, so they conflict with let/const declarations of the same name.
        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                && (declType == Token.LET || declType == Token.CONST)
                && currentScope != currentScriptOrFn
                && (currentScope.hasFunctionNameInBlock(name)
                        || currentScope.hasGeneratorNameInBlock(name))) {
            addError("msg.let.redecl", name);
            return false;
        }
        // ES6: Check for function/generator declaration conflicts in blocks.
        // Per Annex B.3.3.4: duplicate entries in LexicallyDeclaredNames are allowed in non-strict
        // mode ONLY if they are all bound by FunctionDeclarations (not generators).
        // So: function+function in non-strict = OK, all other combinations = error.
        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                && declType == Token.FUNCTION
                && !isFunctionInStatementPosition
                && currentScope != currentScriptOrFn) {
            // Generator conflicting with prior function or generator: always error
            if (isGenerator
                    && (currentScope.hasFunctionNameInBlock(name)
                            || currentScope.hasGeneratorNameInBlock(name))) {
                addError("msg.fn.redecl", name);
                return false;
            }
            // Regular function conflicting with prior generator: always error
            if (!isGenerator && currentScope.hasGeneratorNameInBlock(name)) {
                addError("msg.fn.redecl", name);
                return false;
            }
            // Regular function conflicting with prior function: error in strict mode only
            if (!isGenerator && currentScope.hasFunctionNameInBlock(name) && inUseStrictDirective) {
                addError("msg.fn.redecl", name);
                return false;
            }
        }
        // ES6: Check for function declaration conflicting with var in the same block.
        // Function declarations in blocks are lexically scoped in ES6, so they cannot
        // coexist with var declarations of the same name in the same block.
        // Per sec-block-static-semantics-early-errors: "It is a Syntax Error if any
        // element of the LexicallyDeclaredNames of StatementList also occurs in the
        // VarDeclaredNames of StatementList."
        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                && declType == Token.FUNCTION
                && !isFunctionInStatementPosition
                && currentScope != currentScriptOrFn
                && currentScope.hasVarNameInBlock(name)) {
            addError("msg.var.redecl", name);
            return false;
        }
        // ES6 modules: Check for generator declaration conflicting with var at module top level.
        // In modules, generator declarations are lexically scoped, so they cannot coexist
        // with var declarations of the same name.
        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                && declType == Token.FUNCTION
                && isGenerator
                && parsingModule
                && currentScope == currentScriptOrFn
                && symbol != null
                && symbol.getDeclType() == Token.VAR) {
            addError("msg.var.redecl", name);
            return false;
        }
        // ES6: Check for var declaration conflicting with function/generator in enclosing blocks.
        // Since var hoists through blocks, we need to check all enclosing block scopes.
        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6 && declType == Token.VAR) {
            for (Scope s = currentScope;
                    s != null && s != currentScriptOrFn;
                    s = s.getParentScope()) {
                if (s.hasFunctionNameInBlock(name) || s.hasGeneratorNameInBlock(name)) {
                    addError("msg.var.redecl", name);
                    return false;
                }
            }
            // ES6 modules: Also check the module top level, since function/generator declarations
            // are lexically scoped in modules (unlike scripts where they're var-scoped).
            if (parsingModule
                    && (currentScriptOrFn.hasFunctionNameInBlock(name)
                            || currentScriptOrFn.hasGeneratorNameInBlock(name))) {
                addError("msg.var.redecl", name);
                return false;
            }
        }
        // Annex B.3.3.3: In non-strict eval code, function declarations that
        // would conflict with let/const should skip hoisting rather than error
        boolean isAnnexBFunctionInEval =
                declType == Token.FUNCTION
                        && compilerEnv.isInEval()
                        && !inUseStrictDirective
                        && symbol != null
                        && (symDeclType == Token.LET || symDeclType == Token.CONST);
        if (symbol != null
                && ((definingScope == currentScope && symDeclType == Token.CONST)
                        || (definingScope == currentScope && symDeclType == Token.LET)
                        || (definingScope == currentScope && declType == Token.CONST)
                        || (definingScope == currentScope
                                && declType == Token.LET
                                && symDeclType == Token.VAR)
                        || varInSameBlock)) {
            // Annex B.3.3.3: Skip hoisting instead of error for function declarations
            if (isAnnexBFunctionInEval) {
                return true;
            }
            // Choose the error message based on what's being redeclared
            String errorId;
            if (symDeclType == Token.CONST) {
                errorId = "msg.const.redecl";
            } else if (symDeclType == Token.LET) {
                // Use different message when var tries to redeclare let
                errorId =
                        (declType == Token.VAR || declType == Token.FUNCTION)
                                ? "msg.let.redecl.by.var"
                                : "msg.let.redecl";
            } else if (symDeclType == Token.VAR) {
                errorId = "msg.var.redecl";
            } else if (symDeclType == Token.FUNCTION) {
                errorId = "msg.fn.redecl";
            } else {
                errorId = "msg.parm.redecl";
            }
            addError(errorId, name);
            return false;
        }
        switch (declType) {
            case Token.LET:
                if (!ignoreNotInBlock
                        && ((currentScope.getType() == Token.IF) || currentScope instanceof Loop)) {
                    addError("msg.let.decl.not.in.block");
                    return false;
                }
                currentScope.putSymbol(new Symbol(declType, name));
                return false;

            case Token.CONST:
                if (!ignoreNotInBlock
                        && ((currentScope.getType() == Token.IF) || currentScope instanceof Loop)) {
                    addError("msg.const.decl.not.in.block");
                    return false;
                }
                currentScope.putSymbol(new Symbol(declType, name));
                return false;

            case Token.VAR:
            case Token.FUNCTION:
                if (symbol != null) {
                    // Check if var/function tries to redeclare a let/const
                    if (symDeclType == Token.LET || symDeclType == Token.CONST) {
                        // Annex B.3.3.3: In non-strict eval code, function declarations
                        // inside blocks that would conflict with let/const should skip
                        // hoisting rather than throwing an error. The function remains
                        // block-scoped only.
                        if (declType == Token.FUNCTION
                                && compilerEnv.isInEval()
                                && !inUseStrictDirective) {
                            // Skip the function binding in the outer scope
                            return true;
                        }
                        addError(
                                symDeclType == Token.LET
                                        ? "msg.let.redecl.by.var"
                                        : "msg.const.redecl",
                                name);
                        return false;
                    } else if (symDeclType == Token.VAR) {
                        addStrictWarning("msg.var.redecl", name);
                    } else if (symDeclType == Token.LP) {
                        addStrictWarning("msg.var.hides.arg", name);
                    }
                } else {
                    currentScriptOrFn.putSymbol(new Symbol(declType, name));
                }
                // Record var declaration in all enclosing block scopes for let/const conflict
                // detection.
                // This is needed because var hoists to function scope, but the ES6 spec requires
                // detecting conflicts with let/const at the block level. Since var hoists through
                // nested blocks, we need to mark the var name in all parent scopes up to the
                // function.
                if (declType == Token.VAR) {
                    for (Scope s = currentScope;
                            s != null && s != currentScriptOrFn;
                            s = s.getParentScope()) {
                        s.addVarNameInBlock(name);
                    }
                }
                // ES6: Record function/generator declaration for conflict detection.
                // In modules, top-level function/generator declarations are lexically scoped.
                // In blocks, function declarations are also lexically scoped in ES6.
                // We track regular functions and generators separately because Annex B.3.3.4
                // allows duplicate function declarations in non-strict mode, but not generators.
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                        && declType == Token.FUNCTION) {
                    boolean isModuleTopLevel = parsingModule && currentScope == currentScriptOrFn;
                    boolean isBlockLevel = currentScope != currentScriptOrFn;
                    if (isModuleTopLevel || isBlockLevel) {
                        if (isGenerator) {
                            currentScope.addGeneratorNameInBlock(name);
                        } else {
                            currentScope.addFunctionNameInBlock(name);
                        }
                    }
                }
                return false;

            case Token.LP:
                if (symbol != null) {
                    // must be duplicate parameter. Second parameter hides the
                    // first, so go ahead and add the second parameter
                    addWarning("msg.dup.parms", name);
                }
                currentScriptOrFn.putSymbol(new Symbol(declType, name));
                return false;

            default:
                throw codeBug();
        }
    }

    private AstNode expr(boolean allowTrailingComma) throws IOException {
        AstNode pn = assignExpr();
        int pos = pn.getPosition();
        while (matchToken(Token.COMMA, true)) {
            int opPos = ts.tokenBeg;
            if (compilerEnv.isStrictMode() && !pn.hasSideEffects())
                addStrictWarning("msg.no.side.effects", "", pos, nodeEnd(pn) - pos);
            // In ES6 generators, yield is a valid expression that can appear after comma
            if (peekToken() == Token.YIELD && !isCurrentFunctionGenerator()) {
                reportError("msg.yield.parenthesized");
            }
            if (allowTrailingComma && peekToken() == Token.RP) {
                pn.putIntProp(Node.TRAILING_COMMA, 1);
                return pn;
            }
            pn = new InfixExpression(Token.COMMA, pn, assignExpr(), opPos);
        }
        return pn;
    }

    /** Check if current function is an ES6 generator */
    boolean isCurrentFunctionGenerator() {
        // We might be inside the function body OR inside function parameters
        // (for generator functions, yield is also a keyword in the parameter list)
        if (!insideFunctionBody() && !insideFunctionParams()) {
            return false;
        }
        if (currentScriptOrFn instanceof FunctionNode) {
            return ((FunctionNode) currentScriptOrFn).isES6Generator();
        }
        return false;
    }

    private AstNode assignExpr() throws IOException {
        int tt = peekToken();
        // Only treat yield as keyword when inside a generator function
        // In ES6 non-strict mode outside generators, yield is a valid identifier
        if (tt == Token.YIELD && isCurrentFunctionGenerator()) {
            return returnOrYield(tt, true);
        }

        // Intentionally not calling lineNumber/columnNumber here!
        // We have not consumed any token yet, so the position would be invalid
        int startLine = ts.lineno, startColumn = ts.getTokenColumn();

        AstNode pn = condExpr();
        boolean hasEOL = false;
        tt = peekTokenOrEOL();
        if (tt == Token.EOL) {
            hasEOL = true;
            tt = peekToken();
        }
        if (Token.FIRST_ASSIGN <= tt && tt <= Token.LAST_ASSIGN) {
            consumeToken();

            // Pull out JSDoc info and reset it before recursing.
            Comment jsdocNode = getAndResetJsDoc();

            markDestructuring(pn);
            int opPos = ts.tokenBeg;
            if (isNotValidSimpleAssignmentTarget(pn))
                reportError("msg.syntax.invalid.assignment.lhs");

            pn = new Assignment(tt, pn, assignExpr(), opPos);

            if (jsdocNode != null) {
                pn.setJsDocNode(jsdocNode);
            }
        } else if (tt == Token.SEMI) {
            // This may be dead code added intentionally, for JSDoc purposes.
            // For example: /** @type Number */ C.prototype.x;
            if (currentJsDocComment != null) {
                pn.setJsDocNode(getAndResetJsDoc());
            }
        } else if (!hasEOL && tt == Token.ARROW) {
            consumeToken();
            // Check for async arrow function: async (...) => or async x =>
            if (isAsyncArrowFunctionCall(pn)) {
                pn = asyncArrowFunction(pn, startLine, startColumn);
            } else {
                pn = arrowFunction(pn, startLine, startColumn);
            }
        } else if (pn.getIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 0) == 1
                && !inDestructuringAssignment
                && !inPotentialArrowParams
                && !inForInit) {
            // Report error for destructuring-style object literals outside of valid contexts.
            // When inPotentialArrowParams is true, defer the check to parenExpr which can
            // properly detect if => follows.
            // When inForInit is true, we're parsing the init part of a for loop - destructuring
            // is valid for for-in/for-of loops; the check is deferred until we know the loop type.
            reportError("msg.syntax");
        }
        return pn;
    }

    private static boolean isNotValidSimpleAssignmentTarget(AstNode pn) {
        if (pn.getType() == Token.GETPROP)
            return isNotValidSimpleAssignmentTarget(((PropertyGet) pn).getLeft());
        return pn.getType() == Token.QUESTION_DOT;
    }

    /**
     * Continues expression parsing from a pre-parsed unary expression. This chains through all
     * binary operators and handles assignment operators and arrow functions. Used when we've
     * already consumed and parsed the leftmost part of an expression (e.g., after consuming 'async'
     * and parsing 'async()').
     */
    private AstNode exprContinuation(AstNode pn) throws IOException {
        // Chain through binary operators (from lowest to highest precedence going up,
        // but we're going down from unary level through each operator level)
        pn = expExprContinuation(pn);
        pn = mulExprContinuation(pn);
        pn = addExprContinuation(pn);
        pn = shiftExprContinuation(pn);
        pn = relExprContinuation(pn);
        pn = eqExprContinuation(pn);
        pn = bitAndExprContinuation(pn);
        pn = bitXorExprContinuation(pn);
        pn = bitOrExprContinuation(pn);
        pn = andExprContinuation(pn);
        pn = orExprContinuation(pn);
        pn = nullishCoalescingExprContinuation(pn);
        pn = condExprContinuation(pn);

        // Now handle assignment operators and arrow functions (like in assignExpr)
        int startLine = pn.getLineno(), startColumn = pn.getColumn();
        boolean hasEOL = false;
        int tt = peekTokenOrEOL();
        if (tt == Token.EOL) {
            hasEOL = true;
            tt = peekToken();
        }
        if (Token.FIRST_ASSIGN <= tt && tt <= Token.LAST_ASSIGN) {
            consumeToken();
            Comment jsdocNode = getAndResetJsDoc();
            markDestructuring(pn);
            int opPos = ts.tokenBeg;
            if (isNotValidSimpleAssignmentTarget(pn))
                reportError("msg.syntax.invalid.assignment.lhs");
            pn = new Assignment(tt, pn, assignExpr(), opPos);
            if (jsdocNode != null) {
                pn.setJsDocNode(jsdocNode);
            }
        } else if (tt == Token.SEMI) {
            if (currentJsDocComment != null) {
                pn.setJsDocNode(getAndResetJsDoc());
            }
        } else if (!hasEOL && tt == Token.ARROW) {
            consumeToken();
            if (isAsyncArrowFunctionCall(pn)) {
                pn = asyncArrowFunction(pn, startLine, startColumn);
            } else {
                pn = arrowFunction(pn, startLine, startColumn);
            }
        }

        return pn;
    }

    // Binary operator continuation methods - continue parsing from a pre-parsed left operand

    private AstNode expExprContinuation(AstNode pn) throws IOException {
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            if (tt == Token.EXP) {
                if (pn instanceof UnaryExpression) {
                    reportError(
                            "msg.no.unary.expr.on.left.exp",
                            AstNode.operatorToString(pn.getType()));
                    return makeErrorNode();
                }
                consumeToken();
                pn = new InfixExpression(tt, pn, expExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode mulExprContinuation(AstNode pn) throws IOException {
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.MUL:
                case Token.DIV:
                case Token.MOD:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, expExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode addExprContinuation(AstNode pn) throws IOException {
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            if (tt == Token.ADD || tt == Token.SUB) {
                consumeToken();
                pn = new InfixExpression(tt, pn, mulExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode shiftExprContinuation(AstNode pn) throws IOException {
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.LSH:
                case Token.URSH:
                case Token.RSH:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, addExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode relExprContinuation(AstNode pn) throws IOException {
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.IN:
                    if (inForInit) break;
                // fall through
                case Token.INSTANCEOF:
                case Token.LE:
                case Token.LT:
                case Token.GE:
                case Token.GT:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, shiftExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode eqExprContinuation(AstNode pn) throws IOException {
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.EQ:
                case Token.NE:
                case Token.SHEQ:
                case Token.SHNE:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, relExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode bitAndExprContinuation(AstNode pn) throws IOException {
        while (matchToken(Token.BITAND, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITAND, pn, eqExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitXorExprContinuation(AstNode pn) throws IOException {
        while (matchToken(Token.BITXOR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITXOR, pn, bitAndExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitOrExprContinuation(AstNode pn) throws IOException {
        while (matchToken(Token.BITOR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITOR, pn, bitXorExpr(), opPos);
        }
        return pn;
    }

    private AstNode andExprContinuation(AstNode pn) throws IOException {
        while (matchToken(Token.AND, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.AND, pn, bitOrExpr(), opPos);
        }
        return pn;
    }

    private AstNode orExprContinuation(AstNode pn) throws IOException {
        while (matchToken(Token.OR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.OR, pn, andExpr(), opPos);
        }
        return pn;
    }

    private AstNode nullishCoalescingExprContinuation(AstNode pn) throws IOException {
        while (matchToken(Token.NULLISH_COALESCING, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.NULLISH_COALESCING, pn, orExpr(), opPos);
        }
        return pn;
    }

    private AstNode condExprContinuation(AstNode pn) throws IOException {
        if (matchToken(Token.HOOK, true)) {
            int qmarkPos = ts.tokenBeg, colonPos = -1;
            boolean wasInForInit = inForInit;
            inForInit = false;
            AstNode ifTrue;
            try {
                ifTrue = assignExpr();
            } finally {
                inForInit = wasInForInit;
            }
            if (mustMatchToken(Token.COLON, "msg.no.colon.cond", true)) colonPos = ts.tokenBeg;
            AstNode ifFalse = assignExpr();
            int beg = pn.getPosition(), len = getNodeEnd(ifFalse) - beg;
            ConditionalExpression ce = new ConditionalExpression(beg, len);
            ce.setLineColumnNumber(pn.getLineno(), pn.getColumn());
            ce.setTestExpression(pn);
            ce.setTrueExpression(ifTrue);
            ce.setFalseExpression(ifFalse);
            ce.setQuestionMarkPosition(qmarkPos - beg);
            ce.setColonPosition(colonPos - beg);
            pn = ce;
        }
        return pn;
    }

    private AstNode condExpr() throws IOException {
        AstNode pn = nullishCoalescingExpr();
        if (matchToken(Token.HOOK, true)) {
            int qmarkPos = ts.tokenBeg, colonPos = -1;
            /*
             * Always accept the 'in' operator in the middle clause of a ternary,
             * where it's unambiguous, even if we might be parsing the init of a
             * for statement.
             */
            boolean wasInForInit = inForInit;
            inForInit = false;
            AstNode ifTrue;
            try {
                ifTrue = assignExpr();
            } finally {
                inForInit = wasInForInit;
            }
            if (mustMatchToken(Token.COLON, "msg.no.colon.cond", true)) colonPos = ts.tokenBeg;
            AstNode ifFalse = assignExpr();
            int beg = pn.getPosition(), len = getNodeEnd(ifFalse) - beg;
            ConditionalExpression ce = new ConditionalExpression(beg, len);
            ce.setLineColumnNumber(pn.getLineno(), pn.getColumn());
            ce.setTestExpression(pn);
            ce.setTrueExpression(ifTrue);
            ce.setFalseExpression(ifFalse);
            ce.setQuestionMarkPosition(qmarkPos - beg);
            ce.setColonPosition(colonPos - beg);
            pn = ce;
        }
        return pn;
    }

    private AstNode nullishCoalescingExpr() throws IOException {
        AstNode pn = orExpr();
        if (matchToken(Token.NULLISH_COALESCING, true)) {
            int opPos = ts.tokenBeg;
            AstNode rn = nullishCoalescingExpr();

            // Cannot immediately contain, or be contained within, an && or || operation.
            if (pn.getType() == Token.OR
                    || pn.getType() == Token.AND
                    || rn.getType() == Token.OR
                    || rn.getType() == Token.AND) {
                reportError("msg.nullish.bad.token");
            }

            pn = new InfixExpression(Token.NULLISH_COALESCING, pn, rn, opPos);
        }
        return pn;
    }

    private AstNode orExpr() throws IOException {
        AstNode pn = andExpr();
        if (matchToken(Token.OR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.OR, pn, orExpr(), opPos);
        }
        return pn;
    }

    private AstNode andExpr() throws IOException {
        AstNode pn = bitOrExpr();
        if (matchToken(Token.AND, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.AND, pn, andExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitOrExpr() throws IOException {
        AstNode pn = bitXorExpr();
        while (matchToken(Token.BITOR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITOR, pn, bitXorExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitXorExpr() throws IOException {
        AstNode pn = bitAndExpr();
        while (matchToken(Token.BITXOR, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITXOR, pn, bitAndExpr(), opPos);
        }
        return pn;
    }

    private AstNode bitAndExpr() throws IOException {
        AstNode pn = eqExpr();
        while (matchToken(Token.BITAND, true)) {
            int opPos = ts.tokenBeg;
            pn = new InfixExpression(Token.BITAND, pn, eqExpr(), opPos);
        }
        return pn;
    }

    private AstNode eqExpr() throws IOException {
        AstNode pn = relExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.EQ:
                case Token.NE:
                case Token.SHEQ:
                case Token.SHNE:
                    consumeToken();
                    int parseToken = tt;
                    if (compilerEnv.getLanguageVersion() == Context.VERSION_1_2) {
                        // JavaScript 1.2 uses shallow equality for == and != .
                        if (tt == Token.EQ) parseToken = Token.SHEQ;
                        else if (tt == Token.NE) parseToken = Token.SHNE;
                    }
                    pn = new InfixExpression(parseToken, pn, relExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode relExpr() throws IOException {
        AstNode pn = shiftExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.IN:
                    if (inForInit) break;
                // fall through
                case Token.INSTANCEOF:
                case Token.LE:
                case Token.LT:
                case Token.GE:
                case Token.GT:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, shiftExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode shiftExpr() throws IOException {
        AstNode pn = addExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.LSH:
                case Token.URSH:
                case Token.RSH:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, addExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode addExpr() throws IOException {
        AstNode pn = mulExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            if (tt == Token.ADD || tt == Token.SUB) {
                consumeToken();
                pn = new InfixExpression(tt, pn, mulExpr(), opPos);
                continue;
            }
            break;
        }
        return pn;
    }

    private AstNode mulExpr() throws IOException {
        AstNode pn = expExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.MUL:
                case Token.DIV:
                case Token.MOD:
                    consumeToken();
                    pn = new InfixExpression(tt, pn, expExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    private AstNode expExpr() throws IOException {
        AstNode pn = unaryExpr();
        for (; ; ) {
            int tt = peekToken(), opPos = ts.tokenBeg;
            switch (tt) {
                case Token.EXP:
                    if (pn instanceof UnaryExpression) {
                        reportError(
                                "msg.no.unary.expr.on.left.exp",
                                AstNode.operatorToString(pn.getType()));
                        return makeErrorNode();
                    }
                    consumeToken();
                    pn = new InfixExpression(tt, pn, expExpr(), opPos);
                    continue;
            }
            break;
        }
        return pn;
    }

    /**
     * Checks if the given node is an identifier reference, unwrapping parenthesized expressions.
     * This is used for strict mode checks like delete on an identifier.
     */
    private boolean isIdentifierReference(AstNode node) {
        while (node instanceof ParenthesizedExpression) {
            node = ((ParenthesizedExpression) node).getExpression();
        }
        return node instanceof Name;
    }

    private AstNode unaryExpr() throws IOException {
        AstNode node;
        int tt = peekToken();
        if (tt == Token.COMMENT) {
            consumeToken();
            tt = peekUntilNonComment(tt);
        }
        int line, column;

        switch (tt) {
            case Token.VOID:
            case Token.NOT:
            case Token.BITNOT:
            case Token.TYPEOF:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                node = new UnaryExpression(tt, ts.tokenBeg, unaryExpr());
                node.setLineColumnNumber(line, column);
                return node;

            case Token.AWAIT:
                // await is a unary operator in async functions
                if (inAsyncFunction) {
                    return awaitExpr();
                }
                // In module code, await is reserved - report error
                if (parsingModule) {
                    consumeToken();
                    reportError("msg.syntax");
                    return new ErrorNode();
                }
                // Outside async functions and module code, treat as identifier
                {
                    AstNode pn = memberExpr(true);
                    tt = peekTokenOrEOL();
                    if (!(tt == Token.INC || tt == Token.DEC)) {
                        return pn;
                    }
                    consumeToken();
                    UpdateExpression uexpr = new UpdateExpression(tt, ts.tokenBeg, pn, true);
                    uexpr.setLineColumnNumber(pn.getLineno(), pn.getColumn());
                    checkBadIncDec(uexpr);
                    return uexpr;
                }

            case Token.ADD:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                // Convert to special POS token in parse tree
                node = new UnaryExpression(Token.POS, ts.tokenBeg, unaryExpr());
                node.setLineColumnNumber(line, column);
                return node;

            case Token.SUB:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                // Convert to special NEG token in parse tree
                node = new UnaryExpression(Token.NEG, ts.tokenBeg, unaryExpr());
                node.setLineColumnNumber(line, column);
                return node;

            case Token.INC:
            case Token.DEC:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                UpdateExpression expr = new UpdateExpression(tt, ts.tokenBeg, memberExpr(true));
                expr.setLineColumnNumber(line, column);
                checkBadIncDec(expr);
                return expr;

            case Token.DELPROP:
                consumeToken();
                line = lineNumber();
                column = columnNumber();
                AstNode operand = unaryExpr();
                // In strict mode, delete on an identifier is a SyntaxError
                // This includes parenthesized identifiers like delete ((x))
                if (inUseStrictDirective && isIdentifierReference(operand)) {
                    reportError("msg.no.delete.strict.id");
                }
                node = new UnaryExpression(tt, ts.tokenBeg, operand);
                node.setLineColumnNumber(line, column);
                return node;

            case Token.ERROR:
                consumeToken();
                return makeErrorNode();
            case Token.LT:
                // XML stream encountered in expression.
                if (compilerEnv.isXmlAvailable()) {
                    consumeToken();
                    return memberExprTail(true, xmlInitializer());
                }
            // Fall thru to the default handling of RELOP
            // fall through

            default:
                AstNode pn = memberExpr(true);
                // Don't look across a newline boundary for a postfix incop.
                tt = peekTokenOrEOL();
                if (!(tt == Token.INC || tt == Token.DEC)) {
                    return pn;
                }
                consumeToken();
                UpdateExpression uexpr = new UpdateExpression(tt, ts.tokenBeg, pn, true);
                uexpr.setLineColumnNumber(pn.getLineno(), pn.getColumn());
                checkBadIncDec(uexpr);
                return uexpr;
        }
    }

    private AstNode xmlInitializer() throws IOException {
        if (currentToken != Token.LT) codeBug();
        int pos = ts.tokenBeg, tt = ts.getFirstXMLToken();
        if (tt != Token.XML && tt != Token.XMLEND) {
            reportError("msg.syntax");
            return makeErrorNode();
        }

        XmlLiteral pn = new XmlLiteral(pos);
        pn.setLineColumnNumber(lineNumber(), columnNumber());

        for (; ; tt = ts.getNextXMLToken()) {
            switch (tt) {
                case Token.XML:
                    pn.addFragment(new XmlString(ts.tokenBeg, ts.getString()));
                    mustMatchToken(Token.LC, "msg.syntax", true);
                    int beg = ts.tokenBeg;
                    AstNode expr =
                            (peekToken() == Token.RC)
                                    ? new EmptyExpression(beg, ts.tokenEnd - beg)
                                    : expr(false);
                    mustMatchToken(Token.RC, "msg.syntax", true);
                    XmlExpression xexpr = new XmlExpression(beg, expr);
                    xexpr.setIsXmlAttribute(ts.isXMLAttribute());
                    xexpr.setLength(ts.tokenEnd - beg);
                    pn.addFragment(xexpr);
                    break;

                case Token.XMLEND:
                    pn.addFragment(new XmlString(ts.tokenBeg, ts.getString()));
                    return pn;

                default:
                    reportError("msg.syntax");
                    return makeErrorNode();
            }
        }
    }

    private List<AstNode> argumentList() throws IOException {
        if (matchToken(Token.RP, true)) return null;

        List<AstNode> result = new ArrayList<>();
        boolean wasInForInit = inForInit;
        inForInit = false;
        try {
            do {
                if (peekToken() == Token.RP) {
                    // Quick fix to handle scenario like f1(a,); but not f1(a,b
                    break;
                }
                // In ES6 generators, yield is a valid expression in function arguments
                if (peekToken() == Token.YIELD && !isCurrentFunctionGenerator()) {
                    reportError("msg.yield.parenthesized");
                }
                AstNode en;
                // Handle spread operator in function call arguments
                if (peekToken() == Token.DOTDOTDOT
                        && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    consumeToken();
                    int spreadPos = ts.tokenBeg;
                    int spreadLineno = lineNumber();
                    int spreadColumn = columnNumber();
                    AstNode exprNode = assignExpr();
                    en = new Spread(spreadPos, ts.tokenEnd - spreadPos);
                    en.setLineColumnNumber(spreadLineno, spreadColumn);
                    ((Spread) en).setExpression(exprNode);
                } else {
                    en = assignExpr();
                }
                if (peekToken() == Token.FOR) {
                    try {
                        result.add(generatorExpression(en, 0, true));
                    } catch (IOException ex) {
                        // #TODO
                    }
                } else {
                    result.add(en);
                }
            } while (matchToken(Token.COMMA, true));
        } finally {
            inForInit = wasInForInit;
        }

        mustMatchToken(Token.RP, "msg.no.paren.arg", true);
        return result;
    }

    /**
     * Parse a new-expression, or if next token isn't {@link Token#NEW}, a primary expression.
     *
     * @param allowCallSyntax passed down to {@link #memberExprTail}
     */
    private AstNode memberExpr(boolean allowCallSyntax) throws IOException {
        int tt = peekToken();
        AstNode pn;

        if (tt != Token.NEW) {
            pn = primaryExpr();
        } else {
            consumeToken();
            int pos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();

            // Check for new.target meta-property
            if (matchToken(Token.DOT, true)) {
                int next = peekToken();
                if (next == Token.NAME && "target".equals(ts.getString())) {
                    consumeToken();
                    // Validate that new.target is used in a valid context (inside a function)
                    if (!insideFunctionBody() && !insideFunctionParams()) {
                        reportError("msg.new.target.not.function");
                    }
                    KeywordLiteral newTarget =
                            new KeywordLiteral(pos, ts.tokenEnd - pos, Token.NEW_TARGET);
                    newTarget.setLineColumnNumber(lineno, column);
                    return memberExprTail(allowCallSyntax, newTarget);
                }
                // Not "target", report error - new. must be followed by target
                reportError("msg.no.target.after.new");
            }

            NewExpression nx = new NewExpression(pos);

            AstNode target = memberExpr(false);
            int end = getNodeEnd(target);
            nx.setTarget(target);
            nx.setLineColumnNumber(lineno, column);

            int lp = -1;
            if (matchToken(Token.LP, true)) {
                lp = ts.tokenBeg;
                List<AstNode> args = argumentList();
                if (args != null && args.size() > ARGC_LIMIT)
                    reportError("msg.too.many.constructor.args");
                int rp = ts.tokenBeg;
                end = ts.tokenEnd;
                if (args != null) nx.setArguments(args);
                nx.setParens(lp - pos, rp - pos);
            }

            // Experimental syntax: allow an object literal to follow a new
            // expression, which will mean a kind of anonymous class built with
            // the JavaAdapter.  the object literal will be passed as an
            // additional argument to the constructor.
            if (matchToken(Token.LC, true)) {
                ObjectLiteral initializer = objectLiteral();
                end = getNodeEnd(initializer);
                nx.setInitializer(initializer);
            }
            nx.setLength(end - pos);
            pn = nx;
        }
        return memberExprTail(allowCallSyntax, pn);
    }

    /**
     * Parse any number of "(expr)", "[expr]" ".expr", "?.expr", "..expr", ".(expr)" or "?.(expr)"
     * constructs trailing the passed expression.
     *
     * @param pn the non-null parent node
     * @return the outermost (lexically last occurring) expression, which will have the passed
     *     parent node as a descendant
     */
    private AstNode memberExprTail(boolean allowCallSyntax, AstNode pn) throws IOException {
        // we no longer return null for errors, so this won't be null
        if (pn == null) codeBug();
        int pos = pn.getPosition();
        int lineno, column;
        boolean isOptionalChain = false;
        tailLoop:
        for (; ; ) {
            lineno = lineNumber();
            column = columnNumber();
            int tt = peekToken();
            switch (tt) {
                case Token.DOT:
                case Token.QUESTION_DOT:
                case Token.DOTDOT:
                    isOptionalChain |= (tt == Token.QUESTION_DOT);
                    pn = propertyAccess(tt, pn, isOptionalChain);
                    break;

                case Token.DOTQUERY:
                    consumeToken();
                    int opPos = ts.tokenBeg, rp = -1;
                    mustHaveXML();
                    setRequiresActivation();
                    AstNode filter = expr(false);
                    int end = getNodeEnd(filter);
                    if (mustMatchToken(Token.RP, "msg.no.paren", true)) {
                        rp = ts.tokenBeg;
                        end = ts.tokenEnd;
                    }
                    XmlDotQuery q = new XmlDotQuery(pos, end - pos);
                    q.setLeft(pn);
                    q.setRight(filter);
                    q.setOperatorPosition(opPos);
                    q.setRp(rp - pos);
                    q.setLineColumnNumber(lineno, column);
                    pn = q;
                    break;

                case Token.LB:
                    consumeToken();
                    pn = makeElemGet(pn, ts.tokenBeg);
                    break;

                case Token.LP:
                    if (!allowCallSyntax) {
                        break tailLoop;
                    }
                    boolean hasEolBeforeLp = (currentFlaggedToken & TI_AFTER_EOL) != 0;
                    pn = makeFunctionCall(pn, pos, isOptionalChain, hasEolBeforeLp);
                    break;
                case Token.COMMENT:
                    // Ignoring all the comments, because previous statement may not be terminated
                    // properly.
                    int currentFlagTOken = currentFlaggedToken;
                    peekUntilNonComment(tt);
                    currentFlaggedToken =
                            (currentFlaggedToken & TI_AFTER_EOL) != 0
                                    ? currentFlaggedToken
                                    : currentFlagTOken;
                    break;
                case Token.TEMPLATE_LITERAL:
                    consumeToken();
                    pn = taggedTemplateLiteral(pn);
                    break;
                default:
                    break tailLoop;
            }
        }
        return pn;
    }

    private FunctionCall makeFunctionCall(
            AstNode pn, int pos, boolean isOptionalChain, boolean hasLineTerminatorBeforeLp)
            throws IOException {
        consumeToken();
        checkCallRequiresActivation(pn);
        FunctionCall f = new FunctionCall(pos);
        f.setTarget(pn);
        f.setLp(ts.tokenBeg - pos);
        f.setHasLineTerminatorBeforeLp(hasLineTerminatorBeforeLp);
        List<AstNode> args = argumentList();
        if (args != null && args.size() > ARGC_LIMIT) reportError("msg.too.many.function.args");
        f.setArguments(args);
        f.setRp(ts.tokenBeg - pos);
        f.setLength(ts.tokenEnd - pos);
        if (isOptionalChain) {
            f.markIsOptionalCall();
        }
        return f;
    }

    private AstNode taggedTemplateLiteral(AstNode pn) throws IOException {
        AstNode templateLiteral = templateLiteral(true);
        TaggedTemplateLiteral tagged = new TaggedTemplateLiteral();
        tagged.setTarget(pn);
        tagged.setTemplateLiteral(templateLiteral);
        tagged.setLineColumnNumber(pn.getLineno(), pn.getColumn());
        return tagged;
    }

    /**
     * Handles any construct following a "." or ".." operator.
     *
     * @param pn the left-hand side (target) of the operator. Never null.
     * @param isOptionalChain whether we are inside an optional chain, i.e. whether a preceding
     *     property access was done via the {@code ?.} operator
     * @return a PropertyGet, XmlMemberGet, or ErrorNode
     */
    private AstNode propertyAccess(int tt, AstNode pn, boolean isOptionalChain) throws IOException {
        if (pn == null) codeBug();
        if (pn.getType() == Token.SUPER && isOptionalChain) {
            reportError("msg.optional.super");
            return makeErrorNode();
        }

        int memberTypeFlags = 0,
                lineno = lineNumber(),
                dotPos = ts.tokenBeg,
                column = columnNumber();
        consumeToken();

        if (tt == Token.DOTDOT) {
            mustHaveXML();
            memberTypeFlags = Node.DESCENDANTS_FLAG;
        }

        AstNode ref = null; // right side of . or .. operator
        boolean isPrivateAccess = false; // ES2022 private property access
        int token = nextToken();
        switch (token) {
            case Token.THROW:
                // needed for generator.throw();
                saveNameTokenData(ts.tokenBeg, "throw", lineNumber(), columnNumber());
                ref = propertyName(-1, memberTypeFlags);
                break;

            case Token.NAME:
                // handles: name, ns::name, ns::*, ns::[expr]
                ref = propertyName(-1, memberTypeFlags);
                break;

            case Token.PRIVATE_NAME:
                // handles: obj.#privateName
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    // Create a Name node for the private property (without the # prefix)
                    String privateName = ts.getString();
                    Name name = new Name(ts.tokenBeg, privateName);
                    name.setLineColumnNumber(lineNumber(), columnNumber());
                    ref = name;
                    isPrivateAccess = true;
                    break;
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            case Token.MUL:
                if (compilerEnv.isXmlAvailable()) {
                    // handles: *, *::name, *::*, *::[expr]
                    saveNameTokenData(ts.tokenBeg, "*", lineNumber(), columnNumber());
                    ref = propertyName(-1, memberTypeFlags);
                    break;
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            case Token.XMLATTR:
                if (compilerEnv.isXmlAvailable()) {
                    // handles: '@attr', '@ns::attr', '@ns::*', '@ns::*',
                    //          '@::attr', '@::*', '@*', '@*::attr', '@*::*'
                    ref = attributeAccess();
                    break;
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            case Token.RESERVED:
                {
                    String name = ts.getString();
                    saveNameTokenData(ts.tokenBeg, name, lineNumber(), columnNumber());
                    ref = propertyName(-1, memberTypeFlags);
                    break;
                }

            case Token.LB:
                if (tt == Token.QUESTION_DOT) {
                    // a ?.[ expr ]
                    consumeToken();
                    ElementGet g = makeElemGet(pn, ts.tokenBeg);
                    g.setType(Token.QUESTION_DOT);
                    return g;
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            case Token.LP:
                if (tt == Token.QUESTION_DOT) {
                    // a function call such as f?.() - no line terminator relevance here
                    return makeFunctionCall(pn, pn.getPosition(), isOptionalChain, false);
                } else {
                    reportError("msg.no.name.after.dot");
                    return makeErrorNode();
                }

            default:
                if (compilerEnv.isReservedKeywordAsIdentifier()) {
                    // allow keywords as property names, e.g. ({if: 1})
                    String name = Token.keywordToName(token);
                    if (name != null) {
                        saveNameTokenData(ts.tokenBeg, name, lineNumber(), columnNumber());
                        ref = propertyName(-1, memberTypeFlags);
                        break;
                    }
                }
                reportError("msg.no.name.after.dot");
                return makeErrorNode();
        }

        boolean xml = ref instanceof XmlRef;
        InfixExpression result = xml ? new XmlMemberGet() : new PropertyGet();
        if (xml && tt == Token.DOT) result.setType(Token.DOT);
        if (isOptionalChain) {
            result.setType(Token.QUESTION_DOT);
        }
        if (isPrivateAccess) {
            result.setType(Token.GETPROP_PRIVATE);
        }
        int pos = pn.getPosition();
        result.setPosition(pos);
        result.setLength(getNodeEnd(ref) - pos);
        result.setOperatorPosition(dotPos - pos);
        result.setLineColumnNumber(lineno, column);
        result.setLeft(pn); // do this after setting position
        result.setRight(ref);
        return result;
    }

    private ElementGet makeElemGet(AstNode pn, int lb) throws IOException {
        int pos = pn.getPosition();
        AstNode expr = expr(false);
        int end = getNodeEnd(expr);
        int rb = -1;
        if (mustMatchToken(Token.RB, "msg.no.bracket.index", true)) {
            rb = ts.tokenBeg;
            end = ts.tokenEnd;
        }
        ElementGet g = new ElementGet(pos, end - pos);
        g.setTarget(pn);
        g.setElement(expr);
        g.setParens(lb, rb);
        return g;
    }

    /**
     * Xml attribute expression:
     *
     * <p>{@code @attr}, {@code @ns::attr}, {@code @ns::*}, {@code @ns::*}, {@code @*},
     * {@code @*::attr}, {@code @*::*}, {@code @ns::[expr]}, {@code @*::[expr]}, {@code @[expr]}
     *
     * <p>Called if we peeked an '@' token.
     */
    private AstNode attributeAccess() throws IOException {
        int tt = nextToken(), atPos = ts.tokenBeg;

        switch (tt) {
            // handles: @name, @ns::name, @ns::*, @ns::[expr]
            case Token.NAME:
                return propertyName(atPos, 0);
            case Token.RESERVED:
                String name = ts.getString();
                saveNameTokenData(ts.tokenBeg, name, lineNumber(), columnNumber());
                return propertyName(atPos, 0);
            // handles: @*, @*::name, @*::*, @*::[expr]
            case Token.MUL:
                saveNameTokenData(ts.tokenBeg, "*", lineNumber(), columnNumber());
                return propertyName(atPos, 0);

            // handles @[expr]
            case Token.LB:
                return xmlElemRef(atPos, null, -1);

            default:
                {
                    if (compilerEnv.isReservedKeywordAsIdentifier()) {
                        // allow keywords as property names, e.g. ({if: 1})
                        name = Token.keywordToName(tt);
                        if (name != null) {
                            saveNameTokenData(ts.tokenBeg, name, lineNumber(), columnNumber());
                            return propertyName(atPos, 0);
                        }
                    }
                }
                reportError("msg.no.name.after.xmlAttr");
                return makeErrorNode();
        }
    }

    /**
     * Check if :: follows name in which case it becomes a qualified name.
     *
     * @param atPos a natural number if we just read an '@' token, else -1
     * @param memberTypeFlags flags tracking whether we're a '.' or '..' child
     * @return an XmlRef node if it's an attribute access, a child of a '..' operator, or the name
     *     is followed by ::. For a plain name, returns a Name node. Returns an ErrorNode for
     *     malformed XML expressions. (For now - might change to return a partial XmlRef.)
     */
    private AstNode propertyName(int atPos, int memberTypeFlags) throws IOException {
        int pos = atPos != -1 ? atPos : ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        int colonPos = -1;
        Name name = createNameNode(true, currentToken);
        Name ns = null;

        if (matchToken(Token.COLONCOLON, true)) {
            ns = name;
            colonPos = ts.tokenBeg;

            int nt = nextToken();
            switch (nt) {
                // handles name::name
                case Token.NAME:
                    name = createNameNode();
                    break;
                case Token.RESERVED:
                    {
                        String realName = ts.getString();
                        saveNameTokenData(ts.tokenBeg, realName, lineNumber(), columnNumber());
                        name = createNameNode(false, -1);
                        break;
                    }
                case Token.MUL:
                    saveNameTokenData(ts.tokenBeg, "*", lineNumber(), columnNumber());
                    name = createNameNode(false, -1);
                    break;

                // handles name::[expr] or *::[expr]
                case Token.LB:
                    return xmlElemRef(atPos, ns, colonPos);

                default:
                    {
                        if (compilerEnv.isReservedKeywordAsIdentifier()) {
                            // allow keywords as property names, e.g. ({if: 1})
                            String realName = Token.keywordToName(nt);
                            if (name != null) {
                                saveNameTokenData(
                                        ts.tokenBeg, realName, lineNumber(), columnNumber());
                                name = createNameNode(false, -1);
                                break;
                            }
                        }
                    }
                    reportError("msg.no.name.after.coloncolon");
                    return makeErrorNode();
            }
        }

        if (ns == null && memberTypeFlags == 0 && atPos == -1) {
            return name;
        }

        XmlPropRef ref = new XmlPropRef(pos, getNodeEnd(name) - pos);
        ref.setAtPos(atPos);
        ref.setNamespace(ns);
        ref.setColonPos(colonPos);
        ref.setPropName(name);
        ref.setLineColumnNumber(lineno, column);
        return ref;
    }

    /**
     * Parse the [expr] portion of an xml element reference, e.g. @[expr], @*::[expr], or
     * ns::[expr].
     */
    private XmlElemRef xmlElemRef(int atPos, Name namespace, int colonPos) throws IOException {
        int lb = ts.tokenBeg, rb = -1, pos = atPos != -1 ? atPos : lb;
        AstNode expr = expr(false);
        int end = getNodeEnd(expr);
        if (mustMatchToken(Token.RB, "msg.no.bracket.index", true)) {
            rb = ts.tokenBeg;
            end = ts.tokenEnd;
        }
        XmlElemRef ref = new XmlElemRef(pos, end - pos);
        ref.setNamespace(namespace);
        ref.setColonPos(colonPos);
        ref.setAtPos(atPos);
        ref.setExpression(expr);
        ref.setBrackets(lb, rb);
        return ref;
    }

    private AstNode destructuringAssignExpr() throws IOException, ParserException {
        try {
            inDestructuringAssignment = true;
            return assignExpr();
        } finally {
            inDestructuringAssignment = false;
        }
    }

    private AstNode destructuringPrimaryExpr() throws IOException, ParserException {
        try {
            inDestructuringAssignment = true;
            return primaryExpr();
        } finally {
            inDestructuringAssignment = false;
        }
    }

    private AstNode primaryExpr() throws IOException {
        int ttFlagged = peekFlaggedToken();
        int tt = ttFlagged & CLEAR_TI_MASK;

        switch (tt) {
            case Token.FUNCTION:
                consumeToken();
                return function(FunctionNode.FUNCTION_EXPRESSION);

            case Token.CLASS:
                consumeToken();
                return classDeclaration(ClassNode.CLASS_EXPRESSION);

            case Token.LB:
                consumeToken();
                return arrayLiteral();

            case Token.LC:
                consumeToken();
                return objectLiteral();

            case Token.LET:
                // In non-strict mode, 'let' can be an identifier
                if (!inUseStrictDirective
                        && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    consumeToken();
                    int letPos = ts.tokenBeg;
                    int letLineno = lineNumber();
                    int letColumn = columnNumber();
                    int nextTT = peekToken();
                    // 'let (' is a let-expression (SpiderMonkey extension)
                    if (nextTT == Token.LP) {
                        return let(false, letPos);
                    }
                    // Otherwise, treat 'let' as an identifier
                    saveNameTokenData(letPos, "let", letLineno, letColumn);
                    return createNameNode(true, Token.NAME);
                }
                consumeToken();
                return let(false, ts.tokenBeg);

            case Token.YIELD:
                // In ES6 non-strict mode outside generators, 'yield' can be used as an identifier
                if (!inUseStrictDirective
                        && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6
                        && !isCurrentFunctionGenerator()) {
                    consumeToken();
                    int yieldPos = ts.tokenBeg;
                    int yieldLineno = lineNumber();
                    int yieldColumn = columnNumber();
                    // Treat 'yield' as an identifier
                    saveNameTokenData(yieldPos, "yield", yieldLineno, yieldColumn);
                    return createNameNode(true, Token.NAME);
                }
                // Inside generators, yield as expression should go through assignExpr ->
                // returnOrYield
                // If we reach here inside a generator, it's a syntax error
                consumeToken();
                reportError("msg.syntax");
                break;

            case Token.AWAIT:
                // Outside async functions and module code, 'await' can be used as an identifier
                // In module code, 'await' is always reserved (ES2017 12.1.1)
                // Inside async functions, await should go through assignExpr -> awaitExpr
                if (!inAsyncFunction && !parsingModule) {
                    consumeToken();
                    int awaitPos = ts.tokenBeg;
                    int awaitLineno = lineNumber();
                    int awaitColumn = columnNumber();
                    // Treat 'await' as an identifier
                    saveNameTokenData(awaitPos, "await", awaitLineno, awaitColumn);
                    return createNameNode(true, Token.NAME);
                }
                // Inside async functions or module code, should not reach here
                consumeToken();
                reportError("msg.syntax");
                break;

            case Token.ASYNC:
                // 'async' can be used as an identifier in expression context
                // async function expressions are handled elsewhere
                consumeToken();
                {
                    int asyncPos = ts.tokenBeg;
                    int asyncLineno = lineNumber();
                    int asyncColumn = columnNumber();
                    boolean asyncContainsEscape = ts.identifierContainsEscape();
                    // Check if followed by 'function' for async function expression
                    // Only allow if async was not written with escapes
                    if (!asyncContainsEscape
                            && peekToken() == Token.FUNCTION
                            && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                        consumeToken();
                        boolean isGenerator = matchToken(Token.MUL, true);
                        return function(FunctionNode.FUNCTION_EXPRESSION, false, isGenerator, true);
                    }
                    // Check for async arrow function with single identifier param: async x => ...
                    // Must check for no line terminator between async and the identifier
                    // Only allow if async was not written with escapes
                    int peekTT = peekTokenOrEOL();
                    if (!asyncContainsEscape
                            && peekTT == Token.NAME
                            && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                        // Speculatively parse the identifier and check for =>
                        consumeToken();
                        int paramPos = ts.tokenBeg;
                        String paramName = ts.getString();
                        int paramLineno = lineNumber();
                        int paramColumn = columnNumber();
                        // Check if => follows (without line terminator)
                        int arrowTT = peekTokenOrEOL();
                        if (arrowTT == Token.ARROW) {
                            // This is async x => ... - parse as async arrow function
                            consumeToken(); // consume =>
                            saveNameTokenData(paramPos, paramName, paramLineno, paramColumn);
                            AstNode param = createNameNode(true, Token.NAME);
                            return asyncArrowFunctionWithParam(
                                    param, asyncPos, asyncLineno, asyncColumn);
                        }
                        // Not an async arrow function - backtrack
                        // We consumed the identifier, need to put it back somehow
                        // Actually, we can't easily backtrack in this parser
                        // So we'll create a synthetic FunctionCall node for async(x)
                        // But this isn't quite right either...
                        // For now, let's report an error or handle differently
                        // Actually, async x where x is not followed by => is a syntax error anyway
                        reportError("msg.syntax");
                        return makeErrorNode();
                    }
                    // Treat 'async' as an identifier
                    saveNameTokenData(
                            asyncPos, "async", asyncLineno, asyncColumn, asyncContainsEscape);
                    return createNameNode(true, Token.NAME);
                }

            case Token.LP:
                consumeToken();
                return parenExpr();

            case Token.XMLATTR:
                consumeToken();
                mustHaveXML();
                return attributeAccess();

            case Token.NAME:
                consumeToken();
                return name(ttFlagged, tt);

            case Token.NUMBER:
            case Token.BIGINT:
                {
                    consumeToken();
                    return createNumericLiteral(tt, false);
                }

            case Token.STRING:
                consumeToken();
                return createStringLiteral();

            case Token.DIV:
            case Token.ASSIGN_DIV:
                consumeToken();
                // Got / or /= which in this context means a regexp
                ts.readRegExp(tt);
                int pos = ts.tokenBeg, end = ts.tokenEnd;
                RegExpLiteral re = new RegExpLiteral(pos, end - pos);
                re.setValue(ts.getString());
                re.setFlags(ts.readAndClearRegExpFlags());
                re.setLineColumnNumber(lineNumber(), columnNumber());
                return re;

            case Token.UNDEFINED:
                {
                    consumeToken();
                    pos = ts.tokenBeg;
                    end = ts.tokenEnd;
                    if (hasUndefinedBeenRedefined) {
                        return new Name(pos, end - pos, "undefined");
                    }

                    KeywordLiteral keywordLiteral = new KeywordLiteral(pos, end - pos, tt);
                    keywordLiteral.setLineColumnNumber(lineNumber(), columnNumber());
                    return keywordLiteral;
                }

            case Token.NULL:
            case Token.THIS:
            case Token.FALSE:
            case Token.TRUE:
                {
                    consumeToken();
                    pos = ts.tokenBeg;
                    end = ts.tokenEnd;
                    KeywordLiteral keywordLiteral = new KeywordLiteral(pos, end - pos, tt);
                    keywordLiteral.setLineColumnNumber(lineNumber(), columnNumber());
                    return keywordLiteral;
                }

            case Token.SUPER:
                if (((insideFunctionParams() || insideFunctionBody()) && insideMethod)
                        || compilerEnv.isAllowSuper()) {
                    consumeToken();
                    pos = ts.tokenBeg;
                    end = ts.tokenEnd;
                    KeywordLiteral keywordLiteral = new KeywordLiteral(pos, end - pos, tt);
                    keywordLiteral.setLineColumnNumber(lineNumber(), columnNumber());
                    return keywordLiteral;
                } else {
                    reportError("msg.super.shorthand.function");
                }
                break;

            case Token.TEMPLATE_LITERAL:
                consumeToken();
                return templateLiteral(false);

            case Token.RESERVED:
                consumeToken();
                reportError("msg.reserved.id", ts.getString());
                break;

            case Token.ERROR:
                consumeToken();
                // the scanner or one of its subroutines reported the error.
                break;

            case Token.EOF:
                consumeToken();
                reportError("msg.unexpected.eof");
                break;

            default:
                consumeToken();
                reportError("msg.syntax");
                break;
        }
        // should only be reachable in IDE/error-recovery mode
        consumeToken();
        return makeErrorNode();
    }

    private AstNode parenExpr() throws IOException {
        boolean wasInForInit = inForInit;
        boolean wasInPotentialArrowParams = inPotentialArrowParams;
        inForInit = false;
        inPotentialArrowParams = true;
        try {
            Comment jsdocNode = getAndResetJsDoc();
            int lineno = lineNumber(), column = columnNumber();
            int begin = ts.tokenBeg;
            AstNode e = (peekToken() == Token.RP ? new EmptyExpression(begin) : expr(true));
            if (peekToken() == Token.FOR) {
                return generatorExpression(e, begin);
            }
            mustMatchToken(Token.RP, "msg.no.paren", true);

            int length = ts.tokenEnd - begin;

            boolean hasObjectLiteralDestructuring =
                    e.getIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 0) == 1;
            boolean hasTrailingComma = e.getIntProp(Node.TRAILING_COMMA, 0) == 1;
            if ((hasTrailingComma || hasObjectLiteralDestructuring || e.getType() == Token.EMPTY)
                    && peekToken() != Token.ARROW) {
                reportError("msg.syntax");
                return makeErrorNode();
            }

            ParenthesizedExpression pn = new ParenthesizedExpression(begin, length, e);
            pn.setLineColumnNumber(lineno, column);
            if (jsdocNode == null) {
                jsdocNode = getAndResetJsDoc();
            }
            if (jsdocNode != null) {
                pn.setJsDocNode(jsdocNode);
            }
            if (hasTrailingComma) {
                pn.putIntProp(Node.TRAILING_COMMA, 1);
            }
            return pn;
        } finally {
            inForInit = wasInForInit;
            inPotentialArrowParams = wasInPotentialArrowParams;
        }
    }

    private AstNode name(int ttFlagged, int tt) throws IOException {
        String nameString = ts.getString();
        int namePos = ts.tokenBeg, nameLineno = lineNumber(), nameColumn = columnNumber();
        if (0 != (ttFlagged & TI_CHECK_LABEL) && peekToken() == Token.COLON) {
            // Do not consume colon.  It is used as an unwind indicator
            // to return to statementHelper.
            Label label = new Label(namePos, ts.tokenEnd - namePos);
            label.setName(nameString);
            label.setLineColumnNumber(lineNumber(), columnNumber());
            return label;
        }
        // Not a label.  Unfortunately peeking the next token to check for
        // a colon has biffed ts.tokenBeg, ts.tokenEnd.  We store the name's
        // bounds in instance vars and createNameNode uses them.
        saveNameTokenData(namePos, nameString, nameLineno, nameColumn);

        if (compilerEnv.isXmlAvailable()) {
            return propertyName(-1, 0);
        }
        return createNameNode(true, Token.NAME);
    }

    /** May return an {@link ArrayLiteral} or {@link ArrayComprehension}. */
    private AstNode arrayLiteral() throws IOException {
        if (currentToken != Token.LB) codeBug();
        int pos = ts.tokenBeg, end = ts.tokenEnd, lineno = lineNumber(), column = columnNumber();
        List<AstNode> elements = new ArrayList<>();
        ArrayLiteral pn = new ArrayLiteral(pos);
        boolean after_lb_or_comma = true;
        int afterComma = -1;
        int skipCount = 0;
        // Reset inForInit to allow `in` operator in array element expressions
        // e.g., for ([x = 'y' in z] of ...) - the `in` is valid in the default value
        // Also set inDestructuringAssignment to allow nested destructuring patterns
        // like [{ x = yield }] where yield is used as an identifier
        boolean wasInForInit = inForInit;
        boolean wasInDestructuringAssignment = inDestructuringAssignment;
        inForInit = false;
        inDestructuringAssignment = true;
        try {
            for (; ; ) {
                int tt = peekToken();
                if (tt == Token.COMMA) {
                    consumeToken();
                    afterComma = ts.tokenEnd;
                    if (!after_lb_or_comma) {
                        after_lb_or_comma = true;
                    } else {
                        elements.add(new EmptyExpression(ts.tokenBeg, 1));
                        skipCount++;
                    }
                } else if (tt == Token.COMMENT) {
                    consumeToken();
                } else if (tt == Token.RB) {
                    consumeToken();
                    // for ([a,] in obj) is legal, but for ([a] in obj) is
                    // not since we have both key and value supplied. The
                    // trick is that [a,] and [a] are equivalent in other
                    // array literal contexts. So we calculate a special
                    // length value just for destructuring assignment.
                    end = ts.tokenEnd;
                    pn.setDestructuringLength(elements.size() + (after_lb_or_comma ? 1 : 0));
                    pn.setSkipCount(skipCount);
                    if (afterComma != -1) warnTrailingComma(pos, elements, afterComma);
                    break;
                } else if (tt == Token.FOR && !after_lb_or_comma && elements.size() == 1) {
                    return arrayComprehension(elements.get(0), pos);
                } else if (tt == Token.EOF) {
                    reportError("msg.no.bracket.arg");
                    break;
                } else {
                    if (!after_lb_or_comma) {
                        reportError("msg.no.bracket.arg");
                    }
                    AstNode element;
                    if (tt == Token.DOTDOTDOT
                            && compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                        consumeToken();
                        int spreadPos = ts.tokenBeg;
                        int spreadLineno = lineNumber();
                        int spreadColumn = columnNumber();
                        AstNode exprNode = assignExpr();
                        element = new Spread(spreadPos, ts.tokenEnd - spreadPos);
                        element.setLineColumnNumber(spreadLineno, spreadColumn);
                        ((Spread) element).setExpression(exprNode);
                    } else {
                        element = assignExpr();
                    }
                    elements.add(element);
                    after_lb_or_comma = false;
                    afterComma = -1;
                }
            }
            for (AstNode e : elements) {
                pn.addElement(e);
            }
            pn.setLength(end - pos);
            pn.setLineColumnNumber(lineno, column);
            return pn;
        } finally {
            inForInit = wasInForInit;
            inDestructuringAssignment = wasInDestructuringAssignment;
        }
    }

    /**
     * Parse a JavaScript 1.7 Array comprehension.
     *
     * @param result the first expression after the opening left-bracket
     * @param pos start of LB token that begins the array comprehension
     * @return the array comprehension or an error node
     */
    private AstNode arrayComprehension(AstNode result, int pos) throws IOException {
        List<ArrayComprehensionLoop> loops = new ArrayList<>();
        while (peekToken() == Token.FOR) {
            loops.add(arrayComprehensionLoop());
        }
        int ifPos = -1;
        ConditionData data = null;
        if (peekToken() == Token.IF) {
            consumeToken();
            ifPos = ts.tokenBeg - pos;
            data = condition();
        }
        mustMatchToken(Token.RB, "msg.no.bracket.arg", true);
        ArrayComprehension pn = new ArrayComprehension(pos, ts.tokenEnd - pos);
        pn.setResult(result);
        pn.setLoops(loops);
        if (data != null) {
            pn.setIfPosition(ifPos);
            pn.setFilter(data.condition);
            pn.setFilterLp(data.lp - pos);
            pn.setFilterRp(data.rp - pos);
        }
        return pn;
    }

    private ArrayComprehensionLoop arrayComprehensionLoop() throws IOException {
        if (nextToken() != Token.FOR) codeBug();
        int pos = ts.tokenBeg;
        int eachPos = -1, lp = -1, rp = -1, inPos = -1;
        boolean isForOf = false;
        ArrayComprehensionLoop pn = new ArrayComprehensionLoop(pos);

        pushScope(pn);
        try {
            if (matchToken(Token.NAME, true)) {
                if ("each".equals(ts.getString())) {
                    eachPos = ts.tokenBeg - pos;
                } else {
                    reportError("msg.no.paren.for");
                }
            }
            if (mustMatchToken(Token.LP, "msg.no.paren.for", true)) {
                lp = ts.tokenBeg - pos;
            }

            AstNode iter = null;
            switch (peekToken()) {
                case Token.LB:
                case Token.LC:
                    // handle destructuring assignment
                    iter = destructuringPrimaryExpr();
                    markDestructuring(iter);
                    break;
                case Token.NAME:
                    consumeToken();
                    iter = createNameNode();
                    break;
                default:
                    reportError("msg.bad.var");
            }

            // Define as a let since we want the scope of the variable to
            // be restricted to the array comprehension
            if (iter.getType() == Token.NAME) {
                defineSymbol(Token.LET, ts.getString(), true);
            }

            switch (nextToken()) {
                case Token.IN:
                    inPos = ts.tokenBeg - pos;
                    break;
                case Token.NAME:
                    // ES6 12.1.1: The `of` keyword must not contain escape sequences
                    if ("of".equals(ts.getString()) && !ts.identifierContainsEscape()) {
                        if (eachPos != -1) {
                            reportError("msg.invalid.for.each");
                        }
                        inPos = ts.tokenBeg - pos;
                        isForOf = true;
                        break;
                    }
                // fall through
                default:
                    reportError("msg.in.after.for.name");
            }
            AstNode obj = expr(false);
            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl", true)) rp = ts.tokenBeg - pos;

            pn.setLength(ts.tokenEnd - pos);
            pn.setIterator(iter);
            pn.setIteratedObject(obj);
            pn.setInPosition(inPos);
            pn.setEachPosition(eachPos);
            pn.setIsForEach(eachPos != -1);
            pn.setParens(lp, rp);
            pn.setIsForOf(isForOf);
            return pn;
        } finally {
            popScope();
        }
    }

    private AstNode generatorExpression(AstNode result, int pos) throws IOException {
        return generatorExpression(result, pos, false);
    }

    private AstNode generatorExpression(AstNode result, int pos, boolean inFunctionParams)
            throws IOException {

        List<GeneratorExpressionLoop> loops = new ArrayList<>();
        while (peekToken() == Token.FOR) {
            loops.add(generatorExpressionLoop());
        }
        int ifPos = -1;
        ConditionData data = null;
        if (peekToken() == Token.IF) {
            consumeToken();
            ifPos = ts.tokenBeg - pos;
            data = condition();
        }
        if (!inFunctionParams) {
            mustMatchToken(Token.RP, "msg.no.paren.let", true);
        }
        GeneratorExpression pn = new GeneratorExpression(pos, ts.tokenEnd - pos);
        pn.setResult(result);
        pn.setLoops(loops);
        if (data != null) {
            pn.setIfPosition(ifPos);
            pn.setFilter(data.condition);
            pn.setFilterLp(data.lp - pos);
            pn.setFilterRp(data.rp - pos);
        }
        return pn;
    }

    private GeneratorExpressionLoop generatorExpressionLoop() throws IOException {
        if (nextToken() != Token.FOR) codeBug();
        int pos = ts.tokenBeg;
        int lp = -1, rp = -1, inPos = -1;
        GeneratorExpressionLoop pn = new GeneratorExpressionLoop(pos);

        pushScope(pn);
        try {
            if (mustMatchToken(Token.LP, "msg.no.paren.for", true)) {
                lp = ts.tokenBeg - pos;
            }

            AstNode iter = null;
            switch (peekToken()) {
                case Token.LB:
                case Token.LC:
                    // handle destructuring assignment
                    iter = destructuringPrimaryExpr();
                    markDestructuring(iter);
                    break;
                case Token.NAME:
                    consumeToken();
                    iter = createNameNode();
                    break;
                default:
                    reportError("msg.bad.var");
            }

            // Define as a let since we want the scope of the variable to
            // be restricted to the array comprehension
            if (iter.getType() == Token.NAME) {
                defineSymbol(Token.LET, ts.getString(), true);
            }

            if (mustMatchToken(Token.IN, "msg.in.after.for.name", true)) inPos = ts.tokenBeg - pos;
            AstNode obj = expr(false);
            if (mustMatchToken(Token.RP, "msg.no.paren.for.ctrl", true)) rp = ts.tokenBeg - pos;

            pn.setLength(ts.tokenEnd - pos);
            pn.setIterator(iter);
            pn.setIteratedObject(obj);
            pn.setInPosition(inPos);
            pn.setParens(lp, rp);
            return pn;
        } finally {
            popScope();
        }
    }

    private static final int PROP_ENTRY = 1;
    private static final int GET_ENTRY = 2;
    private static final int SET_ENTRY = 4;
    private static final int METHOD_ENTRY = 8;

    private ObjectLiteral objectLiteral() throws IOException {
        int pos = ts.tokenBeg, lineno = lineNumber(), column = columnNumber();
        int afterComma = -1;
        List<AbstractObjectProperty> elems = new ArrayList<>();
        Set<String> getterNames = null;
        Set<String> setterNames = null;
        if (this.inUseStrictDirective) {
            getterNames = new HashSet<>();
            setterNames = new HashSet<>();
        }
        Comment objJsdocNode = getAndResetJsDoc();
        boolean objectLiteralDestructuringDefault = false;
        commaLoop:
        for (; ; ) {
            String propertyName = null;
            int entryKind = PROP_ENTRY;
            int tt = peekToken();
            Comment jsdocNode = getAndResetJsDoc();
            if (tt == Token.COMMENT) {
                consumeToken();
                tt = peekUntilNonComment(tt);
            }
            if (tt == Token.RC) {
                if (afterComma != -1) warnTrailingComma(pos, elems, afterComma);
                break commaLoop;
            }
            AstNode pname = objliteralProperty();
            if (pname == null) {
                reportError("msg.bad.prop");
            } else if (pname instanceof Spread) {
                AstNode spreadExpr = ((Spread) pname).getExpression();
                if (spreadExpr instanceof Name || spreadExpr instanceof StringLiteral) {
                    // For complicated reasons, parsing a name does not advance the token
                    spreadExpr.setLineColumnNumber(lineNumber(), columnNumber());
                }

                SpreadObjectProperty spreadObjectProperty =
                        new SpreadObjectProperty((Spread) pname);
                elems.add(spreadObjectProperty);
            } else {
                propertyName = ts.getString();
                int ppos = ts.tokenBeg;
                consumeToken();
                if (pname instanceof Name || pname instanceof StringLiteral) {
                    // For complicated reasons, parsing a name does not advance the token
                    pname.setLineColumnNumber(lineNumber(), columnNumber());
                } else if (pname instanceof GeneratorMethodDefinition) {
                    // Same as above
                    ((GeneratorMethodDefinition) pname)
                            .getMethodName()
                            .setLineColumnNumber(lineNumber(), columnNumber());
                }

                // This code path needs to handle both destructuring object
                // literals like:
                // var {get, b} = {get: 1, b: 2};
                // and getters like:
                // var x = {get 1() { return 2; };
                // So we check a whitelist of tokens to check if we're at the
                // first case. (Because of keywords, the second case may be
                // many tokens.)
                int peeked = peekToken();
                if (peeked != Token.COMMA && peeked != Token.COLON && peeked != Token.RC) {
                    if (peeked == Token.ASSIGN) { // we have an object literal with
                        // destructuring assignment and a default value
                        objectLiteralDestructuringDefault = true;
                        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                            elems.add(plainProperty(pname, tt));
                            if (matchToken(Token.COMMA, true)) {
                                continue;
                            } else {
                                break commaLoop;
                            }
                        } else {
                            reportError("msg.default.args");
                        }
                    } else if (peeked == Token.LP) {
                        entryKind = METHOD_ENTRY;
                    } else if (pname.getType() == Token.NAME) {
                        if ("get".equals(propertyName)) {
                            entryKind = GET_ENTRY;
                        } else if ("set".equals(propertyName)) {
                            entryKind = SET_ENTRY;
                        }
                    }
                    if (entryKind == GET_ENTRY || entryKind == SET_ENTRY) {
                        pname = objliteralProperty();
                        if (pname == null) {
                            reportError("msg.bad.prop");
                        }
                        consumeToken();
                    }
                    if (pname == null) {
                        propertyName = null;
                    } else {
                        propertyName = ts.getString();
                        // shorthand method definition
                        ObjectProperty objectProp =
                                methodDefinition(
                                        ppos,
                                        pname,
                                        entryKind,
                                        pname instanceof GeneratorMethodDefinition,
                                        true);
                        pname.setJsDocNode(jsdocNode);
                        elems.add(objectProp);
                    }
                } else {
                    pname.setJsDocNode(jsdocNode);
                    elems.add(plainProperty(pname, tt));
                }
                if (pname instanceof GeneratorMethodDefinition && entryKind != METHOD_ENTRY) {
                    reportError("msg.bad.prop");
                }
            }

            if (this.inUseStrictDirective
                    && propertyName != null
                    && !(pname instanceof ComputedPropertyKey)
                    && compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
                switch (entryKind) {
                    case PROP_ENTRY:
                    case METHOD_ENTRY:
                        if (getterNames.contains(propertyName)
                                || setterNames.contains(propertyName)) {
                            addError("msg.dup.obj.lit.prop.strict", propertyName);
                        }
                        getterNames.add(propertyName);
                        setterNames.add(propertyName);
                        break;
                    case GET_ENTRY:
                        if (getterNames.contains(propertyName)) {
                            addError("msg.dup.obj.lit.prop.strict", propertyName);
                        }
                        getterNames.add(propertyName);
                        break;
                    case SET_ENTRY:
                        if (setterNames.contains(propertyName)) {
                            addError("msg.dup.obj.lit.prop.strict", propertyName);
                        }
                        setterNames.add(propertyName);
                        break;
                }
            }

            // Eat any dangling jsdoc in the property.
            getAndResetJsDoc();

            if (matchToken(Token.COMMA, true)) {
                afterComma = ts.tokenEnd;
            } else {
                break commaLoop;
            }
        }

        mustMatchToken(Token.RC, "msg.no.brace.prop", true);
        ObjectLiteral pn = new ObjectLiteral(pos, ts.tokenEnd - pos);
        if (objectLiteralDestructuringDefault) {
            pn.putIntProp(Node.OBJECT_LITERAL_DESTRUCTURING, 1);
        }
        if (objJsdocNode != null) {
            pn.setJsDocNode(objJsdocNode);
        }
        pn.setElements(elems);
        pn.setLineColumnNumber(lineno, column);
        return pn;
    }

    private AstNode objliteralProperty() throws IOException {
        AstNode pname;
        int tt = peekToken();
        switch (tt) {
            case Token.NAME:
                pname = createNameNode();
                break;

            case Token.STRING:
                pname = createStringLiteral();
                break;

            case Token.NUMBER:
            case Token.BIGINT:
                pname = createNumericLiteral(tt, true);
                break;

            case Token.DOTDOTDOT:
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    int pos = ts.tokenBeg;
                    nextToken();
                    int lineno = lineNumber();
                    int column = columnNumber();

                    AstNode exprNode = assignExpr();
                    pname = new Spread(pos, ts.tokenEnd - pos);
                    pname.setLineColumnNumber(lineno, column);
                    ((Spread) pname).setExpression(exprNode);
                } else {
                    reportError("msg.bad.prop");
                    return null;
                }
                break;

            case Token.LB:
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    int pos = ts.tokenBeg;
                    nextToken();
                    int lineno = lineNumber();
                    int column = columnNumber();
                    AstNode expr = assignExpr();
                    if (peekToken() != Token.RB) {
                        reportError("msg.bad.prop");
                    }
                    nextToken();

                    pname = new ComputedPropertyKey(pos, ts.tokenEnd - pos);
                    pname.setLineColumnNumber(lineno, column);
                    ((ComputedPropertyKey) pname).setExpression(expr);
                } else {
                    reportError("msg.bad.prop");
                    return null;
                }
                break;

            case Token.MUL:
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                    int pos = ts.tokenBeg;
                    nextToken();
                    int lineno = lineNumber();
                    int column = columnNumber();
                    pname = objliteralProperty();

                    pname = new GeneratorMethodDefinition(pos, ts.tokenEnd - pos, pname);
                    pname.setLineColumnNumber(lineno, column);
                } else {
                    reportError("msg.bad.prop");
                    return null;
                }
                break;

            default:
                if (compilerEnv.isReservedKeywordAsIdentifier()
                        && TokenStream.isKeyword(
                                ts.getString(),
                                compilerEnv.getLanguageVersion(),
                                inUseStrictDirective)) {
                    // convert keyword to property name, e.g. ({if: 1})
                    pname = createNameNode();
                    break;
                }
                return null;
        }

        return pname;
    }

    private ObjectProperty plainProperty(AstNode property, int ptt) throws IOException {
        // Support, e.g., |var {x, y} = o| as destructuring shorthand
        // for |var {x: x, y: y} = o|, as implemented in spidermonkey JS 1.8.
        int tt = peekToken();
        if ((tt == Token.COMMA || tt == Token.RC)
                && ptt == Token.NAME
                && compilerEnv.getLanguageVersion() >= Context.VERSION_1_8) {
            if (!inDestructuringAssignment
                    && compilerEnv.getLanguageVersion() < Context.VERSION_ES6) {
                reportError("msg.bad.object.init");
            }
            AstNode nn = new Name(property.getPosition(), property.getString());
            ObjectProperty pn = new ObjectProperty();
            pn.setKeyAndValue(property, nn);
            return pn;
        } else if (tt == Token.ASSIGN) {
            /* we're in destructuring with defaults in a object literal; treat defaults as values */
            ObjectProperty pn = new ObjectProperty();
            consumeToken(); // consume the `=`
            // Reset inForInit to allow `in` operator in default value
            // e.g., for ({ x = 'y' in z } of ...) - the `in` is valid in the default value
            boolean wasInForInit = inForInit;
            inForInit = false;
            try {
                Assignment defaultValue = new Assignment(property, assignExpr());
                defaultValue.setType(Token.ASSIGN);
                pn.setKeyAndValue(property, defaultValue);
            } finally {
                inForInit = wasInForInit;
            }
            return pn;
        }
        mustMatchToken(Token.COLON, "msg.no.colon.prop", true);
        ObjectProperty pn = new ObjectProperty();
        // Reset inForInit to allow `in` operator in property value
        // e.g., for ({ x: prop = 'y' in z } of ...) - the `in` is valid in the default value
        // Also set inDestructuringAssignment to allow nested destructuring patterns like { x: { y =
        // z } }
        boolean wasInForInit = inForInit;
        boolean wasInDestructuringAssignment = inDestructuringAssignment;
        inForInit = false;
        inDestructuringAssignment = true;
        try {
            pn.setKeyAndValue(property, assignExpr());
        } finally {
            inForInit = wasInForInit;
            inDestructuringAssignment = wasInDestructuringAssignment;
        }
        return pn;
    }

    private ObjectProperty methodDefinition(
            int pos, AstNode propName, int entryKind, boolean isGenerator, boolean isShorthand)
            throws IOException {
        // Pass isGenerator so the function body knows it's a generator for yield parsing
        FunctionNode fn = function(FunctionNode.FUNCTION_EXPRESSION, true, isGenerator);

        // Validate getter/setter parameter counts per ES6 spec
        int paramCount = fn.getParams().size();
        if (entryKind == GET_ENTRY && paramCount != 0) {
            reportError("msg.getter.param.count");
        } else if (entryKind == SET_ENTRY && paramCount != 1) {
            reportError("msg.setter.param.count");
        }
        // ES6 14.3.1: Setter parameter cannot be a rest parameter
        if (entryKind == SET_ENTRY && fn.hasRestParameter()) {
            reportError("msg.setter.rest.param");
        }

        // We've already parsed the function name, so fn should be anonymous.
        Name name = fn.getFunctionName();
        if (name != null && name.length() != 0) {
            reportError("msg.bad.prop");
        }
        ObjectProperty pn = new ObjectProperty(pos);
        switch (entryKind) {
            case GET_ENTRY:
                pn.setIsGetterMethod();
                fn.setFunctionIsGetterMethod();
                break;
            case SET_ENTRY:
                pn.setIsSetterMethod();
                fn.setFunctionIsSetterMethod();
                break;
            case METHOD_ENTRY:
                pn.setIsNormalMethod();
                fn.setFunctionIsNormalMethod();
                if (isGenerator) {
                    fn.setIsES6Generator();
                }
                if (isShorthand) {
                    fn.setIsShorthand();
                }
                break;
        }
        int end = getNodeEnd(fn);
        pn.setKeyAndValue(propName, fn);
        pn.setLength(end - pos);
        return pn;
    }

    private Name createNameNode() {
        return createNameNode(false, Token.NAME);
    }

    /**
     * Create a {@code Name} node using the token info from the last scanned name. In some cases we
     * need to either synthesize a name node, or we lost the name token information by peeking. If
     * the {@code token} parameter is not {@link Token#NAME}, then we use token info saved in
     * instance vars.
     */
    private Name createNameNode(boolean checkActivation, int token) {
        int beg = ts.tokenBeg;
        String s = ts.getString();
        int lineno = lineNumber();
        int column = columnNumber();
        boolean containsEscape = ts.identifierContainsEscape();
        if (!"".equals(prevNameTokenString)) {
            beg = prevNameTokenStart;
            s = prevNameTokenString;
            lineno = prevNameTokenLineno;
            column = prevNameTokenColumn;
            containsEscape = prevNameTokenContainsEscape;
            prevNameTokenStart = 0;
            prevNameTokenString = "";
            prevNameTokenLineno = 0;
            prevNameTokenColumn = 0;
            prevNameTokenContainsEscape = false;
        }
        if (s == null) {
            if (compilerEnv.isIdeMode()) {
                s = "";
            } else {
                codeBug();
            }
        }
        Name name = new Name(beg, s);
        name.setLineColumnNumber(lineno, column);
        name.setContainsEscape(containsEscape);
        if (checkActivation) {
            checkActivationName(s, token);
        }
        return name;
    }

    private StringLiteral createStringLiteral() {
        int pos = ts.tokenBeg, end = ts.tokenEnd;
        StringLiteral s = new StringLiteral(pos, end - pos);
        s.setLineColumnNumber(lineNumber(), columnNumber());
        s.setValue(ts.getString());
        s.setQuoteCharacter(ts.getQuoteChar());
        s.setHasEscapes(ts.stringHasEscapes());
        return s;
    }

    private AstNode templateLiteral(boolean isTaggedLiteral) throws IOException {
        if (currentToken != Token.TEMPLATE_LITERAL) codeBug();
        int pos = ts.tokenBeg, end = ts.tokenEnd, lineno = lineNumber(), column = columnNumber();
        List<AstNode> elements = new ArrayList<>();
        TemplateLiteral pn = new TemplateLiteral(pos);

        int posChars = ts.tokenBeg + 1;
        int tt = ts.readTemplateLiteral(isTaggedLiteral);
        while (tt == Token.TEMPLATE_LITERAL_SUBST) {
            elements.add(createTemplateLiteralCharacters(posChars));
            elements.add(expr(false));
            mustMatchToken(Token.RC, "msg.syntax", true);
            posChars = ts.tokenBeg + 1;
            tt = ts.readTemplateLiteral(isTaggedLiteral);
        }
        if (tt == Token.ERROR) {
            return makeErrorNode();
        }
        assert tt == Token.TEMPLATE_LITERAL;
        elements.add(createTemplateLiteralCharacters(posChars));
        end = ts.tokenEnd;
        pn.setElements(elements);
        pn.setLength(end - pos);
        pn.setLineColumnNumber(lineno, column);

        return pn;
    }

    private TemplateCharacters createTemplateLiteralCharacters(int pos) {
        TemplateCharacters chars = new TemplateCharacters(pos, ts.tokenEnd - pos - 1);
        chars.setValue(ts.getString());
        chars.setRawValue(ts.getRawString());
        return chars;
    }

    private AstNode createNumericLiteral(int tt, boolean isProperty) {
        String s = ts.getString();
        if (this.inUseStrictDirective && ts.isNumericOldOctal()) {
            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6 || !isProperty) {
                if (tt == Token.BIGINT) {
                    reportError("msg.no.old.octal.bigint");
                } else {
                    reportError("msg.no.old.octal.strict");
                }
            }
        }
        if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6 || !isProperty) {
            if (ts.isNumericBinary()) {
                s = "0b" + s;
            } else if (ts.isNumericOldOctal()) {
                s = "0" + s;
            } else if (ts.isNumericOctal()) {
                s = "0o" + s;
            } else if (ts.isNumericHex()) {
                s = "0x" + s;
            }
        }

        AstNode result;
        if (tt == Token.BIGINT) {
            result = new BigIntLiteral(ts.tokenBeg, s + "n", ts.getBigInt());
        } else {
            result = new NumberLiteral(ts.tokenBeg, s, ts.getNumber());
        }
        result.setLineColumnNumber(lineNumber(), columnNumber());
        return result;
    }

    protected void checkActivationName(String name, int token) {
        if ("arguments".equals(name) && currentScriptOrFn instanceof FunctionNode) {
            // If there is a usage of "arguments" we need to initialize it. However,
            // we might not be in a function body, because we could be inside a function's
            // default arguments. So, we do this check first, before the "insideFunctionBody"
            ((FunctionNode) currentScriptOrFn).setRequiresArgumentObject();
        }

        if (!insideFunctionBody()) {
            return;
        }
        boolean activation = false;
        if ("arguments".equals(name)
                &&
                // An arrow function not generate arguments. So it not need activation.
                ((FunctionNode) currentScriptOrFn).getFunctionType()
                        != FunctionNode.ARROW_FUNCTION) {
            activation = true;
        } else if (compilerEnv.getActivationNames() != null
                && compilerEnv.getActivationNames().contains(name)) {
            activation = true;
        } else if ("length".equals(name)) {
            if (token == Token.GETPROP && compilerEnv.getLanguageVersion() == Context.VERSION_1_2) {
                // Use of "length" in 1.2 requires an activation object.
                activation = true;
            }
        }
        if (activation) {
            setRequiresActivation();
        }
    }

    protected void setRequiresActivation() {
        if (insideFunctionBody()) {
            ((FunctionNode) currentScriptOrFn).setRequiresActivation();
        }
    }

    private void checkCallRequiresActivation(AstNode pn) {
        if ((pn.getType() == Token.NAME && "eval".equals(((Name) pn).getIdentifier()))
                || (pn.getType() == Token.GETPROP
                        && "eval".equals(((PropertyGet) pn).getProperty().getIdentifier()))) {
            setRequiresActivation();
            setRequiresArgumentObject();
        }
    }

    protected void setIsGenerator() {
        if (insideFunctionBody()) {
            ((FunctionNode) currentScriptOrFn).setIsGenerator();
        }
    }

    private void setRequiresArgumentObject() {
        if (insideFunctionBody()) {
            ((FunctionNode) currentScriptOrFn).setRequiresArgumentObject();
        }
    }

    private void checkBadIncDec(UpdateExpression expr) {
        AstNode op = removeParens(expr.getOperand());
        int tt = op.getType();
        if (!(tt == Token.NAME
                || tt == Token.GETPROP
                || tt == Token.GETELEM
                || tt == Token.GET_REF
                || tt == Token.CALL))
            reportError(expr.getType() == Token.INC ? "msg.bad.incr" : "msg.bad.decr");
        // In strict mode, increment/decrement on arguments/eval is a SyntaxError
        if (inUseStrictDirective && tt == Token.NAME) {
            String name = ((Name) op).getIdentifier();
            if ("eval".equals(name) || "arguments".equals(name)) {
                reportError("msg.bad.id.strict", name);
            }
        }
    }

    private ErrorNode makeErrorNode() {
        ErrorNode pn = new ErrorNode(ts.tokenBeg, ts.tokenEnd - ts.tokenBeg);
        pn.setLineColumnNumber(lineNumber(), columnNumber());
        return pn;
    }

    // Return end of node.  Assumes node does NOT have a parent yet.
    private static int nodeEnd(AstNode node) {
        return node.getPosition() + node.getLength();
    }

    private void saveNameTokenData(int pos, String name, int lineno, int column) {
        saveNameTokenData(pos, name, lineno, column, false);
    }

    private void saveNameTokenData(
            int pos, String name, int lineno, int column, boolean containsEscape) {
        prevNameTokenStart = pos;
        prevNameTokenString = name;
        prevNameTokenLineno = lineno;
        prevNameTokenColumn = column;
        prevNameTokenContainsEscape = containsEscape;
    }

    /**
     * Return the file offset of the beginning of the input source line containing the passed
     * position.
     *
     * @param pos an offset into the input source stream. If the offset is negative, it's converted
     *     to 0, and if it's beyond the end of the source buffer, the last source position is used.
     * @return the offset of the beginning of the line containing pos (i.e. 1+ the offset of the
     *     first preceding newline). Returns -1 if the {@link CompilerEnvirons} is not set to
     *     ide-mode, and {@link #parse(Reader,String,int)} was used.
     */
    private int lineBeginningFor(int pos) {
        if (sourceChars == null) {
            return -1;
        }
        if (pos <= 0) {
            return 0;
        }
        char[] buf = sourceChars;
        if (pos >= buf.length) {
            pos = buf.length - 1;
        }
        while (--pos >= 0) {
            char c = buf[pos];
            if (ScriptRuntime.isJSLineTerminator(c)) {
                return pos + 1; // want position after the newline
            }
        }
        return 0;
    }

    private void warnMissingSemi(int pos, int end) {
        // Should probably change this to be a CompilerEnvirons setting,
        // with an enum Never, Always, Permissive, where Permissive means
        // don't warn for 1-line functions like function (s) {return x+2}
        if (compilerEnv.isStrictMode()) {
            int[] linep = new int[2];
            String line = ts.getLine(end, linep);
            // this code originally called lineBeginningFor() and in order to
            // preserve its different line-offset handling, we need to special
            // case ide-mode here
            int beg = compilerEnv.isIdeMode() ? Math.max(pos, end - linep[1]) : pos;
            if (line != null) {
                addStrictWarning("msg.missing.semi", "", beg, end - beg, linep[0], line, linep[1]);
            } else {
                // no line information available, report warning at current line
                addStrictWarning("msg.missing.semi", "", beg, end - beg);
            }
        }
    }

    private void warnTrailingComma(int pos, List<?> elems, int commaPos) {
        if (compilerEnv.getWarnTrailingComma()) {
            // back up from comma to beginning of line or array/objlit
            if (!elems.isEmpty()) {
                pos = ((AstNode) elems.get(0)).getPosition();
            }
            pos = Math.max(pos, lineBeginningFor(commaPos));
            addWarning("msg.extra.trailing.comma", pos, commaPos - pos);
        }
    }

    // helps reduce clutter in the already-large function() method
    protected class PerFunctionVariables {
        private ScriptNode savedCurrentScriptOrFn;
        private Scope savedCurrentScope;
        private int savedEndFlags;
        private boolean savedInForInit;
        private Map<String, LabeledStatement> savedLabelSet;
        private List<Loop> savedLoopSet;
        private List<Jump> savedLoopAndSwitchSet;
        private boolean savedHasUndefinedBeenRedefined;
        private boolean savedInAsyncFunction;

        PerFunctionVariables(FunctionNode fnNode) {
            savedCurrentScriptOrFn = Parser.this.currentScriptOrFn;
            Parser.this.currentScriptOrFn = fnNode;

            savedCurrentScope = Parser.this.currentScope;
            Parser.this.currentScope = fnNode;

            savedLabelSet = Parser.this.labelSet;
            Parser.this.labelSet = null;

            savedLoopSet = Parser.this.loopSet;
            Parser.this.loopSet = null;

            savedLoopAndSwitchSet = Parser.this.loopAndSwitchSet;
            Parser.this.loopAndSwitchSet = null;

            savedEndFlags = Parser.this.endFlags;
            Parser.this.endFlags = 0;

            savedInForInit = Parser.this.inForInit;
            Parser.this.inForInit = false;

            savedHasUndefinedBeenRedefined = Parser.this.hasUndefinedBeenRedefined;
            // we want to inherit the current value

            savedInAsyncFunction = Parser.this.inAsyncFunction;
            // Reset for nested functions - will be set appropriately when parsing async functions
            Parser.this.inAsyncFunction = false;
        }

        void restore() {
            Parser.this.currentScriptOrFn = savedCurrentScriptOrFn;
            Parser.this.currentScope = savedCurrentScope;
            Parser.this.labelSet = savedLabelSet;
            Parser.this.loopSet = savedLoopSet;
            Parser.this.loopAndSwitchSet = savedLoopAndSwitchSet;
            Parser.this.endFlags = savedEndFlags;
            Parser.this.inForInit = savedInForInit;
            Parser.this.hasUndefinedBeenRedefined = savedHasUndefinedBeenRedefined;
            Parser.this.inAsyncFunction = savedInAsyncFunction;
        }
    }

    PerFunctionVariables createPerFunctionVariables(FunctionNode fnNode) {
        return new PerFunctionVariables(fnNode);
    }

    /**
     * Given a destructuring assignment with a left hand side parsed as an array or object literal
     * and a right hand side expression, rewrite as a series of assignments to the variables defined
     * in left from property accesses to the expression on the right.
     *
     * @param type declaration type: Token.VAR or Token.LET or -1
     * @param left array or object literal containing NAME nodes for variables to assign
     * @param right expression to assign from
     * @return expression that performs a series of assignments to the variables defined in left
     */
    Node createDestructuringAssignment(
            int type,
            Node left,
            Node right,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter) {
        return createDestructuringAssignment(
                type, left, right, defaultValue, transformer, isFunctionParameter, false);
    }

    Node createDestructuringAssignment(
            int type,
            Node left,
            Node right,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter,
            boolean isForOfDestructuring) {
        String tempName = currentScriptOrFn.getNextTempName();
        Node result =
                destructuringAssignmentHelper(
                        type,
                        left,
                        right,
                        tempName,
                        defaultValue,
                        transformer,
                        isFunctionParameter,
                        isForOfDestructuring);
        Node comma = result.getLastChild();
        comma.addChildToBack(createName(tempName));
        return result;
    }

    Node createDestructuringAssignment(
            int type, Node left, Node right, AstNode defaultValue, Transformer transformer) {
        return createDestructuringAssignment(type, left, right, defaultValue, transformer, true);
    }

    Node createDestructuringAssignment(int type, Node left, Node right, Transformer transformer) {
        return createDestructuringAssignment(type, left, right, null, transformer, false);
    }

    /**
     * Creates a destructuring assignment that uses ES6 iterator protocol. This should be used for
     * for-of loop destructuring where the iterated value needs to be destructured using iterators
     * and properly closed.
     */
    Node createDestructuringAssignmentWithIteratorProtocol(
            int type, Node left, Node right, Transformer transformer) {
        // For for-of, we need iterator protocol AND special handling for empty arrays
        return createDestructuringAssignment(type, left, right, null, transformer, true, true);
    }

    Node createDestructuringAssignment(int type, Node left, Node right, AstNode defaultValue) {
        return createDestructuringAssignment(type, left, right, defaultValue, null, true);
    }

    Node destructuringAssignmentHelper(
            int variableType,
            Node left,
            Node right,
            String tempName,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter) {
        return destructuringAssignmentHelper(
                variableType,
                left,
                right,
                tempName,
                defaultValue,
                transformer,
                isFunctionParameter,
                false);
    }

    Node destructuringAssignmentHelper(
            int variableType,
            Node left,
            Node right,
            String tempName,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter,
            boolean isForOfDestructuring) {
        Scope result = createScopeNode(Token.LETEXPR, left.getLineno(), left.getColumn());
        result.addChildToFront(new Node(Token.LET, createName(Token.NAME, tempName, right)));
        try {
            pushScope(result);
            defineSymbol(Token.LET, tempName, true);
        } finally {
            popScope();
        }
        Node comma = new Node(Token.COMMA);
        result.addChildToBack(comma);
        List<String> destructuringNames = new ArrayList<>();
        boolean empty = true;
        String iteratorName = null;
        String lastResultName = null;
        if (left instanceof ArrayLiteral) {
            DestructuringArrayResult arrayResult =
                    destructuringArray(
                            (ArrayLiteral) left,
                            variableType,
                            tempName,
                            comma,
                            destructuringNames,
                            defaultValue,
                            transformer,
                            isFunctionParameter,
                            isForOfDestructuring);
            empty = arrayResult.empty;
            iteratorName = arrayResult.iteratorName;
            lastResultName = arrayResult.lastResultName;
        } else if (left instanceof ObjectLiteral) {
            empty =
                    destructuringObject(
                            (ObjectLiteral) left,
                            variableType,
                            tempName,
                            comma,
                            destructuringNames,
                            defaultValue,
                            transformer,
                            isFunctionParameter);
        } else if (left.getType() == Token.GETPROP || left.getType() == Token.GETELEM) {
            switch (variableType) {
                case Token.CONST:
                case Token.LET:
                case Token.VAR:
                    reportError("msg.bad.assign.left");
            }
            comma.addChildToBack(simpleAssignment(left, createName(tempName), transformer));
        } else {
            reportError("msg.bad.assign.left");
        }
        if (empty) {
            if (left instanceof ObjectLiteral) {
                // For empty object patterns like `let {} = value`, we still need to check
                // that value is object-coercible (throws TypeError for null/undefined).
                // Use REQ_OBJ_COERCIBLE which checks without accessing any properties.
                Node checkNode = new Node(Token.REQ_OBJ_COERCIBLE, createName(tempName));
                comma.addChildToBack(checkNode);
            } else {
                // Don't want a COMMA node with no children. Just add a zero.
                comma.addChildToBack(createNumber(0));
            }
        }

        // Add iterator closing to the comma sequence if needed
        // ES6 7.4.6 IteratorClose: Call iterator.return() if iterator is not done
        // Generate: !lastResult.done ? ((f = iterator.return) !== undefined ? f.call(iterator) :
        // undefined) : undefined
        // TODO: Add check that return() result is an object per ES6 7.4.6 step 9
        if (iteratorName != null && lastResultName != null) {
            // Allocate temp for return method
            String returnMethodName = currentScriptOrFn.getNextTempName();
            defineSymbol(Token.VAR, returnMethodName, true);

            // Check if iterator is done: !lastResult.done
            Node getDone =
                    new Node(Token.GETPROP, createName(lastResultName), Node.newString("done"));
            Node notDone = new Node(Token.NOT, getDone);

            // Get iterator.return and store: f = iterator.return
            Node getReturn =
                    new Node(Token.GETPROP, createName(iteratorName), Node.newString("return"));
            Node assignReturn =
                    new Node(
                            Token.SETNAME,
                            createName(Token.BINDNAME, returnMethodName, null),
                            getReturn);

            // Check if return method is not undefined: (f = iterator.return) !== undefined
            Node notUndefined = new Node(Token.NE, assignReturn, new Node(Token.UNDEFINED));

            // Call return method: f.call(iterator)
            Node getCall =
                    new Node(Token.GETPROP, createName(returnMethodName), Node.newString("call"));
            Node callReturn = new Node(Token.CALL, getCall);
            callReturn.addChildToBack(createName(iteratorName)); // 'this' argument

            // Inner ternary: (f = iterator.return) !== undefined ? f.call(iterator) : undefined
            Node innerTernary =
                    new Node(Token.HOOK, notUndefined, callReturn, new Node(Token.UNDEFINED));

            // Outer ternary: !lastResult.done ? innerTernary : undefined
            Node outerTernary =
                    new Node(Token.HOOK, notDone, innerTernary, new Node(Token.UNDEFINED));

            comma.addChildToBack(outerTernary);
        }

        result.putProp(Node.DESTRUCTURING_NAMES, destructuringNames);
        return result;
    }

    private static class DestructuringArrayResult {
        boolean empty;
        String iteratorName;
        String lastResultName;

        DestructuringArrayResult(boolean empty, String iteratorName, String lastResultName) {
            this.empty = empty;
            this.iteratorName = iteratorName;
            this.lastResultName = lastResultName;
        }
    }

    DestructuringArrayResult destructuringArray(
            ArrayLiteral array,
            int variableType,
            String tempName,
            Node parent,
            List<String> destructuringNames,
            AstNode defaultValue,
            Transformer transformer,
            boolean isFunctionParameter,
            boolean isForOfDestructuring) {
        boolean empty = true;
        int setOp;
        if (variableType == Token.CONST) {
            setOp = Token.SETCONST;
        } else if (variableType == Token.LET) {
            setOp = Token.SETLETINIT;
        } else {
            setOp = Token.SETNAME;
        }
        int index = 0;
        boolean defaultValuesSetup = false;
        boolean iteratorSetup = false;
        String iteratorName = null;
        String lastResultName = null;

        // ES6+ array destructuring should use iterator protocol (ES6 12.15.5)
        // Pre-ES6 used index-based access for backwards compatibility
        boolean useIteratorProtocol = compilerEnv.getLanguageVersion() >= Context.VERSION_ES6;

        List<AstNode> elements = array.getElements();
        for (int elemIndex = 0; elemIndex < elements.size(); elemIndex++) {
            AstNode n = elements.get(elemIndex);

            // Handle rest element: [...rest]
            if (n.getType() == Token.DOTDOTDOT) {
                // Rest element must be last in the pattern (no elements or trailing comma after)
                // Also check destructuringLength - a trailing comma increments it
                boolean hasTrailingComma = array.getDestructuringLength() > elements.size();
                if (elemIndex != elements.size() - 1 || hasTrailingComma) {
                    reportError("msg.parm.after.rest");
                    return new DestructuringArrayResult(false, iteratorName, lastResultName);
                }

                // Get the binding target from the Spread node
                AstNode restTarget = ((Spread) n).getExpression();

                if (defaultValue != null && !defaultValuesSetup) {
                    setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                    defaultValuesSetup = true;
                }

                Node restValue;

                // In ES6+, use Array.from for rest patterns at position 0 to properly
                // handle iterables (including iterators). This applies to both function
                // parameters and variable declarations.
                if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6 && index == 0) {
                    // Use Array.from(tempName) which properly handles the iterator protocol
                    Node arrayFrom =
                            new Node(Token.GETPROP, createName("Array"), Node.newString("from"));
                    Node fromCall = new Node(Token.CALL, arrayFrom);
                    fromCall.addChildToBack(createName(tempName));
                    restValue = fromCall;
                } else {
                    // Use Array.prototype.slice.call(tempName, index) to collect remaining elements
                    // This works for arrays and array-like objects
                    // Generate: restTarget = Array.prototype.slice.call(tempName, index)
                    Node arrayProto =
                            new Node(
                                    Token.GETPROP,
                                    createName("Array"),
                                    Node.newString("prototype"));
                    Node sliceMethod = new Node(Token.GETPROP, arrayProto, Node.newString("slice"));
                    Node callMethod = new Node(Token.GETPROP, sliceMethod, Node.newString("call"));
                    Node sliceCall = new Node(Token.CALL, callMethod);
                    sliceCall.addChildToBack(createName(tempName));
                    sliceCall.addChildToBack(createNumber(index));
                    restValue = sliceCall;
                }

                if (restTarget.getType() == Token.NAME) {
                    String name = restTarget.getString();
                    parent.addChildToBack(
                            new Node(setOp, createName(Token.BINDNAME, name, null), restValue));
                    if (variableType != -1) {
                        defineSymbol(variableType, name, true);
                        destructuringNames.add(name);
                    }
                } else {
                    // Nested destructuring in rest: [...[a, b]]
                    parent.addChildToBack(
                            destructuringAssignmentHelper(
                                    variableType,
                                    restTarget,
                                    restValue,
                                    currentScriptOrFn.getNextTempName(),
                                    null,
                                    transformer,
                                    isFunctionParameter));
                }
                empty = false;
                break; // Rest element must be last
            }

            if (n.getType() == Token.EMPTY) {
                // For ES6+ iterator protocol, elisions must advance the iterator
                if (useIteratorProtocol) {
                    // Apply default value first if not done yet - this is critical for patterns
                    // like [,] = iter where the default should be used when arg is undefined
                    if (defaultValue != null && !defaultValuesSetup) {
                        setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                        defaultValuesSetup = true;
                    }
                    // Set up iterator if not done yet (lazy initialization)
                    if (!iteratorSetup) {
                        iteratorName = currentScriptOrFn.getNextTempName();
                        lastResultName = currentScriptOrFn.getNextTempName();
                        defineSymbol(Token.VAR, iteratorName, true);
                        defineSymbol(Token.VAR, lastResultName, true);

                        Node symbolName = createName("Symbol");
                        Node getIteratorProp =
                                new Node(Token.GETPROP, symbolName, Node.newString("iterator"));
                        Node getIteratorMethod = new Node(Token.GETELEM, createName(tempName));
                        getIteratorMethod.addChildToBack(getIteratorProp);
                        Node callIterator = new Node(Token.CALL, getIteratorMethod);
                        Node iteratorAssign =
                                new Node(
                                        Token.SETNAME,
                                        createName(Token.BINDNAME, iteratorName, null),
                                        callIterator);
                        parent.addChildToBack(iteratorAssign);
                        iteratorSetup = true;
                        empty = false;
                    }
                    // Advance the iterator for this elision position
                    Node getNextProp =
                            new Node(
                                    Token.GETPROP,
                                    createName(iteratorName),
                                    Node.newString("next"));
                    Node callNext = new Node(Token.CALL, getNextProp);
                    Node storeResult =
                            new Node(
                                    Token.SETNAME,
                                    createName(Token.BINDNAME, lastResultName, null),
                                    callNext);
                    parent.addChildToBack(storeResult);
                }
                index++;
                continue;
            }

            Node rightElem;

            if (defaultValue != null && !defaultValuesSetup) {
                setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                defaultValuesSetup = true;
            }

            // Set up iterator for ES6+ array destructuring (after default value is applied)
            // ES6 12.15.5 requires iterator protocol for array destructuring
            if (useIteratorProtocol && !iteratorSetup) {
                // Allocate temp names for iterator tracking
                iteratorName = currentScriptOrFn.getNextTempName();
                lastResultName = currentScriptOrFn.getNextTempName();
                // Define the iterator temp variables for strict mode
                defineSymbol(Token.VAR, iteratorName, true);
                defineSymbol(Token.VAR, lastResultName, true);

                // Generate: iterator = tempName[Symbol.iterator]()
                // Pure AST: CALL(GETELEM(tempName, GETPROP(NAME("Symbol"), "iterator")))
                Node symbolName = createName("Symbol");
                Node getIteratorProp =
                        new Node(Token.GETPROP, symbolName, Node.newString("iterator"));
                Node getIteratorMethod = new Node(Token.GETELEM, createName(tempName));
                getIteratorMethod.addChildToBack(getIteratorProp);
                Node callIterator = new Node(Token.CALL, getIteratorMethod);
                Node iteratorAssign =
                        new Node(
                                Token.SETNAME,
                                createName(Token.BINDNAME, iteratorName, null),
                                callIterator);
                parent.addChildToBack(iteratorAssign);
                iteratorSetup = true;
                empty = false;
            }

            // Generate code to get element
            if (useIteratorProtocol && iteratorName != null) {
                // ES6+: Call iterator.next() and store the full result to check done later
                Node getNextProp =
                        new Node(Token.GETPROP, createName(iteratorName), Node.newString("next"));
                Node callNext = new Node(Token.CALL, getNextProp);
                Node storeResult =
                        new Node(
                                Token.SETNAME,
                                createName(Token.BINDNAME, lastResultName, null),
                                callNext);
                parent.addChildToBack(storeResult);
                // Extract .value from the result
                String elemTempName = currentScriptOrFn.getNextTempName();
                // Define the element temp variable for strict mode
                defineSymbol(Token.VAR, elemTempName, true);
                Node getValue =
                        new Node(
                                Token.GETPROP, createName(lastResultName), Node.newString("value"));
                Node storeElem =
                        new Node(
                                Token.SETNAME,
                                createName(Token.BINDNAME, elemTempName, null),
                                getValue);
                parent.addChildToBack(storeElem);
                // Use the temp variable for element access
                rightElem = createName(elemTempName);
                empty = false;
            } else {
                // Pre-ES6: index-based access
                rightElem = new Node(Token.GETELEM, createName(tempName), createNumber(index));
            }

            if (n.getType() == Token.NAME) {
                /* [x] = [1] */
                String name = n.getString();
                parent.addChildToBack(
                        new Node(setOp, createName(Token.BINDNAME, name, null), rightElem));
                if (variableType != -1) {
                    defineSymbol(variableType, name, true);
                    destructuringNames.add(name);
                }
            } else if (n.getType() == Token.ASSIGN) {
                /* [x = 1] = [2] */
                processDestructuringDefaults(
                        variableType,
                        parent,
                        destructuringNames,
                        (Assignment) n,
                        rightElem,
                        setOp,
                        transformer,
                        isFunctionParameter);
            } else {
                parent.addChildToBack(
                        destructuringAssignmentHelper(
                                variableType,
                                n,
                                rightElem,
                                currentScriptOrFn.getNextTempName(),
                                null,
                                transformer,
                                isFunctionParameter));
            }
            index++;
            empty = false;
        }

        // For ES6+ empty array patterns in ASSIGNMENT/FOR-OF context, we need to call
        // GetIterator and IteratorClose. But for function parameters (not for-of), empty
        // pattern = no iteration.
        // ES6 12.15.5.2 ArrayAssignmentPattern : [ ] requires GetIterator + IteratorClose
        // ES6 13.3.3.6 ArrayBindingPattern : [ ] just returns NormalCompletion(empty)
        if (empty && useIteratorProtocol && (!isFunctionParameter || isForOfDestructuring)) {
            // Set up iterator to verify the value is iterable (throws TypeError if not)
            iteratorName = currentScriptOrFn.getNextTempName();
            lastResultName = currentScriptOrFn.getNextTempName();
            defineSymbol(Token.VAR, iteratorName, true);
            defineSymbol(Token.VAR, lastResultName, true);

            // Generate: iterator = tempName[Symbol.iterator]()
            Node symbolName = createName("Symbol");
            Node getIteratorProp = new Node(Token.GETPROP, symbolName, Node.newString("iterator"));
            Node getIteratorMethod = new Node(Token.GETELEM, createName(tempName));
            getIteratorMethod.addChildToBack(getIteratorProp);
            Node callIterator = new Node(Token.CALL, getIteratorMethod);
            Node iteratorAssign =
                    new Node(
                            Token.SETNAME,
                            createName(Token.BINDNAME, iteratorName, null),
                            callIterator);
            parent.addChildToBack(iteratorAssign);

            // Create a placeholder result with done=false so IteratorClose will be called
            // Since we didn't call next(), iterator is not done and should be closed
            Node doneFalse = new Node(Token.OBJECTLIT);
            doneFalse.putProp(Node.OBJECT_IDS_PROP, new Object[] {"done"});
            doneFalse.addChildToBack(new Node(Token.FALSE));
            Node resultAssign =
                    new Node(
                            Token.SETNAME,
                            createName(Token.BINDNAME, lastResultName, null),
                            doneFalse);
            parent.addChildToBack(resultAssign);

            empty = false;
        }

        return new DestructuringArrayResult(empty, iteratorName, lastResultName);
    }

    private void processDestructuringDefaults(
            int variableType,
            Node parent,
            List<String> destructuringNames,
            Assignment n,
            Node rightElem,
            int setOp,
            Transformer transformer,
            boolean isFunctionParameter) {
        Node left = n.getLeft();
        Node right = null;
        if (left.getType() == Token.NAME) {
            String name = left.getString();

            right = (transformer != null) ? transformer.transform(n.getRight()) : n.getRight();

            // ES6 function name inference for anonymous functions in destructuring defaults
            if (compilerEnv.getLanguageVersion() >= Context.VERSION_ES6) {
                if (transformer != null) {
                    // When transformer is provided, work with the transformed node
                    inferFunctionNameInDestructuring(left, right);
                } else {
                    // During parsing, work with the AST node directly
                    inferFunctionNameForAstNode(left, n.getRight());
                }
            }

            // Inner condition checks if the destructured value is undefined:
            // ($1[0] === undefined) ? defaultValue : $1[0]
            Node cond_inner =
                    new Node(
                            Token.HOOK,
                            new Node(
                                    Token.SHEQ,
                                    new KeywordLiteral().setType(Token.UNDEFINED),
                                    rightElem),
                            right,
                            rightElem);

            // Both declaration and assignment contexts use the same logic:
            // Check if the destructured value (rightElem) is undefined → use default.
            // Pattern: name = (rightElem === undefined) ? defaultValue : rightElem
            Node valueToAssign = cond_inner;

            // store it to be transformed later
            if (transformer == null) {
                // Pass the name for function name inference of anonymous classes/functions
                currentScriptOrFn.putDestructuringRvalues(cond_inner, right, name);
            }

            parent.addChildToBack(
                    new Node(setOp, createName(Token.BINDNAME, name, null), valueToAssign));
            if (variableType != -1) {
                defineSymbol(variableType, name, true);
                destructuringNames.add(name);
            }
        } else {
            // Handle nested destructuring patterns with defaults, eg: [[x, y, z] = [4, 5, 6]]
            if (left instanceof ArrayLiteral || left instanceof ObjectLiteral) {
                right = (transformer != null) ? transformer.transform(n.getRight()) : n.getRight();

                Node cond_default =
                        new Node(
                                Token.HOOK,
                                new Node(
                                        Token.SHEQ,
                                        new KeywordLiteral().setType(Token.UNDEFINED),
                                        rightElem),
                                right,
                                rightElem);

                if (transformer == null) {
                    currentScriptOrFn.putDestructuringRvalues(cond_default, right);
                }

                parent.addChildToBack(
                        destructuringAssignmentHelper(
                                variableType,
                                left,
                                cond_default,
                                currentScriptOrFn.getNextTempName(),
                                null,
                                transformer,
                                isFunctionParameter));
            } else {
                reportError("msg.bad.assign.left");
            }
        }
    }

    /**
     * Infer function name for anonymous functions used as default values in destructuring patterns.
     * This implements the ES6 SetFunctionName semantic for destructuring bindings.
     */
    private void inferFunctionNameInDestructuring(Node left, Node right) {
        if (!(left instanceof Name) || right == null) {
            return;
        }

        Name name = (Name) left;
        if (name.getIdentifier().equals(NativeObject.PROTO_PROPERTY)) {
            return;
        }

        // Handle anonymous function expressions
        if (right.type == Token.FUNCTION) {
            int fnIndex = right.getExistingIntProp(Node.FUNCTION_PROP);
            FunctionNode functionNode = currentScriptOrFn.getFunctionNode(fnIndex);
            if (functionNode.getFunctionName() == null) {
                functionNode.setFunctionName(name);
            }
        }

        // Handle anonymous class expressions
        if (right.type == Token.CLASS) {
            Node constructorNode = right.getFirstChild();
            if (constructorNode != null && constructorNode.type == Token.FUNCTION) {
                int fnIndex = constructorNode.getExistingIntProp(Node.FUNCTION_PROP);
                FunctionNode functionNode = currentScriptOrFn.getFunctionNode(fnIndex);
                if (functionNode.getFunctionName() == null) {
                    functionNode.setFunctionName(name);
                }
            }
        }
    }

    /**
     * Infer function name for anonymous functions in destructuring defaults during parsing. This
     * handles the case when we're working with AST nodes (FunctionNode) directly rather than
     * transformed IR nodes.
     */
    private void inferFunctionNameForAstNode(Node left, Node right) {
        if (!(left instanceof Name) || right == null) {
            return;
        }

        Name name = (Name) left;
        if (name.getIdentifier().equals(NativeObject.PROTO_PROPERTY)) {
            return;
        }

        // Handle anonymous function expressions (FunctionNode in AST form)
        if (right instanceof FunctionNode) {
            FunctionNode functionNode = (FunctionNode) right;
            if (functionNode.getFunctionName() == null) {
                functionNode.setFunctionName(name);
            }
        }

        // Handle anonymous class expressions (ClassNode in AST form)
        if (right instanceof ClassNode) {
            ClassNode classNode = (ClassNode) right;
            FunctionNode constructor = classNode.getConstructor();
            if (constructor != null && constructor.getFunctionName() == null) {
                constructor.setFunctionName(name);
            }
        }
    }

    static Object getPropKey(Node id) {
        Object key;
        if (id instanceof Name) {
            String s = ((Name) id).getIdentifier();
            key = ScriptRuntime.getIndexObject(s);
        } else if (id instanceof StringLiteral) {
            String s = ((StringLiteral) id).getValue();
            key = ScriptRuntime.getIndexObject(s);
        } else if (id instanceof NumberLiteral) {
            double n = ((NumberLiteral) id).getNumber();
            key = ScriptRuntime.getIndexObject(n);
        } else if (id instanceof GeneratorMethodDefinition) {
            key = getPropKey(((GeneratorMethodDefinition) id).getMethodName());
        } else {
            key = null; // Filled later
        }
        return key;
    }

    private void setupDefaultValues(
            String tempName,
            Node parent,
            AstNode defaultValue,
            int setOp,
            Transformer transformer) {
        if (defaultValue != null) {
            // if there's defaultValue it can be substituted for tempName if that's undefined
            // i.e. $1 = ($1 == undefined) ? defaultValue : $1

            Node defaultRvalue =
                    transformer != null ? transformer.transform(defaultValue) : defaultValue;

            Node undefined = new KeywordLiteral().setType(Token.UNDEFINED);

            Node cond_default =
                    new Node(
                            Token.HOOK,
                            new Node(Token.SHEQ, createName(tempName), undefined),
                            defaultRvalue,
                            createName(tempName));

            if (transformer == null) {
                currentScriptOrFn.putDestructuringRvalues(cond_default, defaultRvalue);
            }

            Node set_default =
                    new Node(setOp, createName(Token.BINDNAME, tempName, null), cond_default);
            parent.addChildToBack(set_default);
        }
    }

    boolean destructuringObject(
            ObjectLiteral node,
            int variableType,
            String tempName,
            Node parent,
            List<String> destructuringNames,
            AstNode defaultValue, /* defaultValue to use in function param decls */
            Transformer transformer,
            boolean isFunctionParameter) {
        boolean empty = true;
        int setOp;
        if (variableType == Token.CONST) {
            setOp = Token.SETCONST;
        } else if (variableType == Token.LET) {
            setOp = Token.SETLETINIT;
        } else {
            setOp = Token.SETNAME;
        }
        boolean defaultValuesSetup = false;

        // For empty patterns like `{} = defaultValue`, we still need to set up default values
        // even though the loop below won't execute
        if (node.getElements().isEmpty() && defaultValue != null) {
            setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
            defaultValuesSetup = true;
        }

        // Track property names for object rest
        List<String> excludedKeys = new ArrayList<>();

        List<AbstractObjectProperty> elements = node.getElements();
        for (int i = 0; i < elements.size(); i++) {
            AbstractObjectProperty abstractProp = elements.get(i);

            if (abstractProp instanceof SpreadObjectProperty) {
                // Object rest must be last element
                if (i != elements.size() - 1) {
                    reportError("msg.parm.after.rest");
                    return false;
                }

                SpreadObjectProperty spreadProp = (SpreadObjectProperty) abstractProp;
                AstNode restTarget = spreadProp.getSpreadNode().getExpression();

                if (defaultValue != null && !defaultValuesSetup) {
                    setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                    defaultValuesSetup = true;
                }

                // Generate object rest copy using Token.OBJECT_REST_COPY
                // This creates a new object with all own enumerable properties except excluded keys
                Node excludedArray = new Node(Token.ARRAYLIT);
                for (String key : excludedKeys) {
                    excludedArray.addChildToBack(Node.newString(key));
                }

                // Create OBJECT_REST_COPY node: source, excludedKeysArray
                Node restCopyNode = new Node(Token.OBJECT_REST_COPY);
                restCopyNode.addChildToBack(createName(tempName));
                restCopyNode.addChildToBack(excludedArray);

                if (restTarget.getType() == Token.NAME) {
                    String name = ((Name) restTarget).getIdentifier();
                    parent.addChildToBack(
                            new Node(setOp, createName(Token.BINDNAME, name, null), restCopyNode));
                    if (variableType != -1) {
                        defineSymbol(variableType, name, true);
                        destructuringNames.add(name);
                    }
                } else {
                    // Nested destructuring in rest: { ...{a, b} } = obj
                    parent.addChildToBack(
                            destructuringAssignmentHelper(
                                    variableType,
                                    restTarget,
                                    restCopyNode,
                                    currentScriptOrFn.getNextTempName(),
                                    null,
                                    transformer,
                                    isFunctionParameter));
                }
                empty = false;
                break;
            }

            ObjectProperty prop = (ObjectProperty) abstractProp;

            int lineno = 0, column = 0;
            // This function is sometimes called from the IRFactory
            // when executing regression tests, and in those cases the
            // tokenStream isn't set.  Deal with it.
            if (ts != null) {
                lineno = lineNumber();
                column = columnNumber();
            }
            AstNode id = prop.getKey();

            // Track the key name for object rest exclusion
            String keyName = null;
            Node rightElem = null;
            if (id instanceof Name) {
                keyName = ((Name) id).getIdentifier();
                Node s = Node.newString(keyName);
                rightElem = new Node(Token.GETPROP, createName(tempName), s);
            } else if (id instanceof StringLiteral) {
                keyName = ((StringLiteral) id).getValue();
                Node s = Node.newString(keyName);
                rightElem = new Node(Token.GETPROP, createName(tempName), s);
            } else if (id instanceof NumberLiteral) {
                keyName = String.valueOf((int) ((NumberLiteral) id).getNumber());
                Node s = createNumber((int) ((NumberLiteral) id).getNumber());
                rightElem = new Node(Token.GETELEM, createName(tempName), s);
            } else if (id instanceof ComputedPropertyKey) {
                // Computed property: { [expr]: value } = obj
                // Transform the expression and use GETELEM
                ComputedPropertyKey cpk = (ComputedPropertyKey) id;
                AstNode keyExpr = cpk.getExpression();
                Node transformedKey;
                if (transformer != null) {
                    transformedKey = transformer.transform(keyExpr);
                } else {
                    // Fallback: create a simple name reference if it's a Name
                    if (keyExpr instanceof Name) {
                        transformedKey = createName(((Name) keyExpr).getIdentifier());
                    } else {
                        reportError("msg.bad.computed.property.in.destruct");
                        return false;
                    }
                }
                rightElem = new Node(Token.GETELEM, createName(tempName), transformedKey);
                // Can't statically know the key for object rest exclusion
                // keyName stays null - computed keys not excluded from rest
            } else {
                throw codeBug();
            }

            if (keyName != null) {
                excludedKeys.add(keyName);
            }

            rightElem.setLineColumnNumber(lineno, column);
            if (defaultValue != null && !defaultValuesSetup) {
                setupDefaultValues(tempName, parent, defaultValue, setOp, transformer);
                defaultValuesSetup = true;
            }

            AstNode value = prop.getValue();
            if (value.getType() == Token.NAME) {
                String name = ((Name) value).getIdentifier();
                parent.addChildToBack(
                        new Node(setOp, createName(Token.BINDNAME, name, null), rightElem));
                if (variableType != -1) {
                    defineSymbol(variableType, name, true);
                    destructuringNames.add(name);
                }
            } else if (value.getType() == Token.ASSIGN) {
                processDestructuringDefaults(
                        variableType,
                        parent,
                        destructuringNames,
                        (Assignment) value,
                        rightElem,
                        setOp,
                        transformer,
                        isFunctionParameter);
            } else {
                parent.addChildToBack(
                        destructuringAssignmentHelper(
                                variableType,
                                value,
                                rightElem,
                                currentScriptOrFn.getNextTempName(),
                                null,
                                transformer,
                                isFunctionParameter));
            }
            empty = false;
        }
        return empty;
    }

    protected Node createName(String name) {
        checkActivationName(name, Token.NAME);
        return Node.newString(Token.NAME, name);
    }

    protected Node createName(int type, String name, Node child) {
        Node result = createName(name);
        result.setType(type);
        if (child != null) result.addChildToBack(child);
        return result;
    }

    protected Node createNumber(double number) {
        return Node.newNumber(number);
    }

    /**
     * Create a node that can be used to hold lexically scoped variable definitions (via let
     * declarations).
     *
     * @param token the token of the node to create
     * @param lineno line number of source
     * @return the created node
     */
    protected Scope createScopeNode(int token, int lineno, int column) {
        Scope scope = new Scope();
        scope.setType(token);
        scope.setLineColumnNumber(lineno, column);
        return scope;
    }

    // Quickie tutorial for some of the interpreter bytecodes.
    //
    // GETPROP - for normal foo.bar prop access; right side is a name
    // GETELEM - for normal foo[bar] element access; rhs is an expr
    // SETPROP - for assignment when left side is a GETPROP
    // SETELEM - for assignment when left side is a GETELEM
    // DELPROP - used for delete foo.bar or foo[bar]
    //
    // GET_REF, SET_REF, DEL_REF - in general, these mean you're using
    // get/set/delete on a right-hand side expression (possibly with no
    // explicit left-hand side) that doesn't use the normal JavaScript
    // Object (i.e. ScriptableObject) get/set/delete functions, but wants
    // to provide its own versions instead.  It will ultimately implement
    // Ref, and currently SpecialRef (for __proto__ etc.) and XmlName
    // (for E4X XML objects) are the only implementations.  The runtime
    // notices these bytecodes and delegates get/set/delete to the object.
    //
    // BINDNAME:  used in assignments.  LHS is evaluated first to get a
    // specific object containing the property ("binding" the property
    // to the object) so that it's always the same object, regardless of
    // side effects in the RHS.
    protected Node simpleAssignment(Node left, Node right) {
        return simpleAssignment(left, right, null, -1);
    }

    protected Node simpleAssignment(Node left, Node right, Transformer transformer) {
        return simpleAssignment(left, right, transformer, -1);
    }

    protected Node simpleAssignment(Node left, Node right, Transformer transformer, int declType) {
        int nodeType = left.getType();
        // Determine the set operation based on declaration type
        // For LET and CONST, use SETLETINIT to allow per-iteration bindings in for-of/for-in
        int setOp;
        if (declType == Token.CONST || declType == Token.LET) {
            setOp = Token.SETLETINIT;
        } else {
            setOp = Token.SETNAME;
        }
        switch (nodeType) {
            case Token.UNDEFINED:
                left = Node.newString(Token.BINDNAME, "undefined");
                return new Node(setOp, left, right);

            case Token.NAME:
                String name = ((Name) left).getIdentifier();
                if (inUseStrictDirective && ("eval".equals(name) || "arguments".equals(name))) {
                    reportError("msg.bad.id.strict", name);
                }
                left.setType(Token.BINDNAME);
                return new Node(setOp, left, right);

            case Token.GETPROP:
            case Token.GETELEM:
                {
                    Node obj, id;
                    // If it's a PropertyGet or ElementGet, we're in the parse pass.
                    // We could alternately have PropertyGet and ElementGet
                    // override getFirstChild/getLastChild and return the appropriate
                    // field, but that seems just as ugly as this casting.
                    if (left instanceof PropertyGet) {
                        AstNode target = ((PropertyGet) left).getTarget();
                        obj = transformer != null ? transformer.transform(target) : target;
                        id = ((PropertyGet) left).getProperty();
                    } else if (left instanceof ElementGet) {
                        AstNode target = ((ElementGet) left).getTarget();
                        AstNode elem = ((ElementGet) left).getElement();
                        obj = transformer != null ? transformer.transform(target) : target;
                        id = transformer != null ? transformer.transform(elem) : elem;
                    } else {
                        // This branch is called during IRFactory transform pass.
                        obj = left.getFirstChild();
                        id = left.getLastChild();
                    }
                    int type;
                    if (nodeType == Token.GETPROP) {
                        type = Token.SETPROP;
                        // TODO(stevey) - see https://bugzilla.mozilla.org/show_bug.cgi?id=492036
                        // The new AST code generates NAME tokens for GETPROP ids where the old
                        // parser
                        // generated STRING nodes. If we don't set the type to STRING below, this
                        // will
                        // cause java.lang.VerifyError in codegen for code like
                        // "var obj={p:3};[obj.p]=[9];"
                        id.setType(Token.STRING);
                    } else {
                        type = Token.SETELEM;
                    }
                    return new Node(type, obj, id, right);
                }
            case Token.GETPROP_PRIVATE:
                {
                    // Private property assignment: this.#x = value
                    Node obj, id;
                    if (left instanceof PropertyGet) {
                        AstNode target = ((PropertyGet) left).getTarget();
                        obj = transformer != null ? transformer.transform(target) : target;
                        id = ((PropertyGet) left).getProperty();
                    } else {
                        obj = left.getFirstChild();
                        id = left.getLastChild();
                    }
                    id.setType(Token.STRING);
                    return new Node(Token.SETPROP_PRIVATE, obj, id, right);
                }
            case Token.GET_REF:
                {
                    Node ref = left.getFirstChild();
                    checkMutableReference(ref);
                    return new Node(Token.SET_REF, ref, right);
                }
        }

        throw codeBug();
    }

    protected void checkMutableReference(Node n) {
        int memberTypeFlags = n.getIntProp(Node.MEMBER_TYPE_PROP, 0);
        if ((memberTypeFlags & Node.DESCENDANTS_FLAG) != 0) {
            reportError("msg.bad.assign.left");
        }
    }

    // remove any ParenthesizedExpression wrappers
    protected AstNode removeParens(AstNode node) {
        while (node instanceof ParenthesizedExpression) {
            node = ((ParenthesizedExpression) node).getExpression();
        }
        return node;
    }

    void markDestructuring(AstNode node) {
        if (node instanceof DestructuringForm) {
            ((DestructuringForm) node).setIsDestructuring(true);
        } else if (node instanceof ParenthesizedExpression) {
            markDestructuring(((ParenthesizedExpression) node).getExpression());
        }
    }

    // throw a failed-assertion with some helpful debugging info
    private RuntimeException codeBug() throws RuntimeException {
        throw Kit.codeBug(
                "ts.cursor="
                        + ts.cursor
                        + ", ts.tokenBeg="
                        + ts.tokenBeg
                        + ", currentToken="
                        + currentToken);
    }

    public boolean inUseStrictDirective() {
        return inUseStrictDirective;
    }

    public void reportErrorsIfExists(int baseLineno) {
        if (this.syntaxErrorCount != 0) {
            String msg = String.valueOf(this.syntaxErrorCount);
            msg = lookupMessage("msg.got.syntax.errors", msg);
            if (!compilerEnv.isIdeMode())
                throw errorReporter.runtimeError(msg, sourceURI, baseLineno, null, 0);
        }
    }

    public void setSourceURI(String sourceURI) {
        this.sourceURI = sourceURI;
    }

    public interface CurrentPositionReporter {
        public int getPosition();

        public int getLength();

        public int getLineno();

        public String getLine();

        public int getOffset();
    }
}
