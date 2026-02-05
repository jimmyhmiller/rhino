/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

import static org.mozilla.javascript.Context.reportError;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.Jump;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.ScriptNode;

/**
 * This class transforms a tree to a lower-level representation for codegen.
 *
 * @see Node
 * @author Norris Boyd
 */
public class NodeTransformer {

    public NodeTransformer() {}

    public final void transform(ScriptNode tree, CompilerEnvirons env) {
        transform(tree, false, env);
    }

    public final void transform(ScriptNode tree, boolean inStrictMode, CompilerEnvirons env) {
        boolean useStrictMode = inStrictMode;
        // Support strict mode inside a function only for "ES6" language level
        // and above. Otherwise, we will end up breaking backward compatibility for
        // many existing scripts.
        if ((env.getLanguageVersion() >= Context.VERSION_ES6) && tree.isInStrictMode()) {
            useStrictMode = true;
        }
        transformCompilationUnit(tree, useStrictMode);
        for (int i = 0; i != tree.getFunctionCount(); ++i) {
            FunctionNode fn = tree.getFunctionNode(i);
            transform(fn, useStrictMode, env);
        }
    }

    private void transformCompilationUnit(ScriptNode tree, boolean inStrictMode) {
        loops = new ArrayDeque<>();
        loopEnds = new ArrayDeque<>();

        // to save against upchecks if no finally blocks are used.
        hasFinally = false;

        // Flatten all only if we are not using scope objects for block scope
        boolean createScopeObjects =
                tree.getType() != Token.FUNCTION || ((FunctionNode) tree).requiresActivation();
        tree.flattenSymbolTable(!createScopeObjects);

        // uncomment to print tree before transformation
        if (Token.printTrees) System.out.println(tree.toStringTree(tree));
        transformCompilationUnit_r(tree, tree, tree, createScopeObjects, inStrictMode);

        // For generator functions, also transform the paramInitBlock which contains
        // parameter destructuring and default value code that runs before Icode_GENERATOR.
        // This needs the same LETEXPR->WITHEXPR transformation as the body.
        if (tree.getType() == Token.FUNCTION) {
            FunctionNode fn = (FunctionNode) tree;
            if (fn.isGenerator()) {
                Node paramInitBlock = fn.getGeneratorParamInitBlock();
                if (paramInitBlock != null) {
                    transformCompilationUnit_r(
                            tree, paramInitBlock, fn, createScopeObjects, inStrictMode);
                }
            }
        }
    }

    private void transformCompilationUnit_r(
            final ScriptNode tree,
            final Node parent,
            Scope scope,
            boolean createScopeObjects,
            boolean inStrictMode) {
        Node node = null;
        siblingLoop:
        for (; ; ) {
            Node previous = null;
            if (node == null) {
                node = parent.getFirstChild();
            } else {
                previous = node;
                node = node.getNext();
            }
            if (node == null) {
                break;
            }

            int type = node.getType();
            if (createScopeObjects
                    && (type == Token.BLOCK || type == Token.LOOP || type == Token.ARRAYCOMP)
                    && (node instanceof Scope)) {
                Scope newScope = (Scope) node;
                if (newScope.getSymbolTable() != null) {
                    // transform to let statement so we get a with statement
                    // created to contain scoped let variables
                    Node let = new Node(type == Token.ARRAYCOMP ? Token.LETEXPR : Token.LET);
                    Node innerLet = new Node(Token.LET);
                    let.addChildToBack(innerLet);
                    for (String name : newScope.getSymbolTable().keySet()) {
                        innerLet.addChildToBack(Node.newString(Token.NAME, name));
                    }
                    newScope.setSymbolTable(null); // so we don't transform again
                    Node oldNode = node;
                    node = replaceCurrent(parent, previous, node, let);
                    type = node.getType();
                    let.addChildToBack(oldNode);
                }
            }

            switch (type) {
                case Token.LABEL:
                case Token.SWITCH:
                    loops.push(node);
                    loopEnds.push(((Jump) node).target);
                    break;

                case Token.LOOP:
                    {
                        // Check if this loop needs per-iteration scope for let bindings
                        if (createScopeObjects
                                && node.getIntProp(Node.PER_ITERATION_SCOPE_PROP, 0) == 1) {
                            @SuppressWarnings("unchecked")
                            List<String> varNames =
                                    (List<String>) node.getProp(Node.PER_ITERATION_NAMES_PROP);
                            if (varNames != null && !varNames.isEmpty()) {
                                wrapLoopBodyWithPerIterationScope(node, varNames);
                            }
                        }
                        loops.push(node);
                        loopEnds.push(((Jump) node).target);
                        break;
                    }

                case Token.WITH:
                    {
                        loops.push(node);
                        Node leave = node.getNext();
                        if (leave.getType() != Token.LEAVEWITH) {
                            Kit.codeBug();
                        }
                        loopEnds.push(leave);
                        break;
                    }

                case Token.TRY:
                    {
                        Jump jump = (Jump) node;
                        Node finallytarget = jump.getFinally();
                        if (finallytarget != null) {
                            hasFinally = true;
                            loops.push(node);
                            loopEnds.push(finallytarget);
                        }
                        break;
                    }

                case Token.TARGET:
                case Token.LEAVEWITH:
                    if (!loopEnds.isEmpty() && loopEnds.peek() == node) {
                        loopEnds.pop();
                        loops.pop();
                    }
                    break;

                case Token.YIELD:
                case Token.YIELD_STAR:
                    ((FunctionNode) tree).addResumptionPoint(node);
                    break;

                case Token.AWAIT:
                    // In async generators, await is also a suspension point that needs
                    // the generator resumption infrastructure (for proper local initialization)
                    if (tree.getType() == Token.FUNCTION
                            && ((FunctionNode) tree).isAsyncGenerator()) {
                        ((FunctionNode) tree).addResumptionPoint(node);
                    }
                    break;

                case Token.RETURN:
                    {
                        boolean isGenerator =
                                tree.getType() == Token.FUNCTION
                                        && ((FunctionNode) tree).isGenerator();
                        if (isGenerator) {
                            node.putIntProp(Node.GENERATOR_END_PROP, 1);
                        }
                        /* If we didn't support try/finally, it wouldn't be
                         * necessary to put LEAVEWITH nodes here... but as
                         * we do need a series of JSR FINALLY nodes before
                         * each RETURN, we need to ensure that each finally
                         * block gets the correct scope... which could mean
                         * that some LEAVEWITH nodes are necessary.
                         */
                        if (!hasFinally) break; // skip the whole mess.
                        Node unwindBlock = null;
                        // Iterate from the top of the stack (most recently inserted) and down
                        for (Node n : loops) {
                            int elemtype = n.getType();
                            if (elemtype == Token.TRY || elemtype == Token.WITH) {
                                Node unwind;
                                if (elemtype == Token.TRY) {
                                    Jump jsrnode = new Jump(Token.JSR);
                                    jsrnode.target = ((Jump) n).getFinally();
                                    unwind = jsrnode;
                                } else {
                                    unwind = new Node(Token.LEAVEWITH);
                                }
                                if (unwindBlock == null) {
                                    unwindBlock = new Node(Token.BLOCK);
                                    unwind.setLineColumnNumber(node.getLineno(), node.getColumn());
                                }
                                unwindBlock.addChildToBack(unwind);
                            }
                        }
                        if (unwindBlock != null) {
                            Node returnNode = node;
                            Node returnExpr = returnNode.getFirstChild();
                            node = replaceCurrent(parent, previous, node, unwindBlock);
                            if (returnExpr == null || isGenerator) {
                                unwindBlock.addChildToBack(returnNode);
                            } else {
                                Node store = new Node(Token.EXPR_RESULT, returnExpr);
                                unwindBlock.addChildToFront(store);
                                returnNode = new Node(Token.RETURN_RESULT);
                                unwindBlock.addChildToBack(returnNode);
                                // transform return expression
                                transformCompilationUnit_r(
                                        tree, store, scope, createScopeObjects, inStrictMode);
                            }
                            // skip transformCompilationUnit_r to avoid infinite loop
                            continue siblingLoop;
                        }
                        break;
                    }

                case Token.BREAK:
                case Token.CONTINUE:
                    {
                        Jump jump = (Jump) node;
                        Jump jumpStatement = jump.getJumpStatement();
                        if (jumpStatement == null) Kit.codeBug();

                        if (loops.isEmpty()) {
                            // Parser/IRFactory ensure that break/continue
                            // always has a jump statement associated with it
                            // which should be found
                            throw Kit.codeBug();
                        }
                        // Iterate from the top of the stack (most recently inserted) and down
                        for (Node n : loops) {
                            if (n == jumpStatement) {
                                break;
                            }

                            int elemtype = n.getType();
                            if (elemtype == Token.WITH) {
                                // Check if this is a per-iteration scope WITH
                                @SuppressWarnings("unchecked")
                                List<String> perIterVars =
                                        (List<String>) n.getProp(Node.PER_ITERATION_NAMES_PROP);
                                if (perIterVars != null && !perIterVars.isEmpty()) {
                                    // Copy each loop variable back to parent scope before leaving
                                    Node copyNode = new Node(Token.COPY_PER_ITER_SCOPE);
                                    copyNode.putProp(
                                            Node.PER_ITERATION_NAMES_PROP,
                                            perIterVars.toArray(new String[0]));
                                    previous = addBeforeCurrent(parent, previous, node, copyNode);
                                }
                                Node leave = new Node(Token.LEAVEWITH);
                                previous = addBeforeCurrent(parent, previous, node, leave);
                            } else if (elemtype == Token.TRY) {
                                Jump tryNode = (Jump) n;
                                Jump jsrFinally = new Jump(Token.JSR);
                                jsrFinally.target = tryNode.getFinally();
                                previous = addBeforeCurrent(parent, previous, node, jsrFinally);
                            }
                        }

                        if (type == Token.BREAK) {
                            jump.target = jumpStatement.target;
                        } else {
                            jump.target = jumpStatement.getContinue();
                        }
                        jump.setType(Token.GOTO);

                        break;
                    }

                case Token.CALL:
                    visitCall(node, tree);
                    break;

                case Token.NEW:
                    visitNew(node, tree);
                    break;

                case Token.LETEXPR:
                case Token.LET:
                    {
                        // For LET scopes from for-loop with const (marked by IRFactory),
                        // we need to create a WITH scope when createScopeObjects is true.
                        // Check this BEFORE the nested-let check since for-loop let scopes
                        // also have a Token.LET first child.
                        if (createScopeObjects
                                && node instanceof Scope
                                && node.getIntProp(Node.CONST_FOR_LOOP_SCOPE, 0) == 1) {
                            Scope letScope = (Scope) node;
                            // Transform to WITH scope for proper const enforcement
                            node = visitLetScopeWithConst(parent, previous, node, letScope);
                            break;
                        }
                        // For LET scopes from for-loop with let that contain function expressions,
                        // we need to create a WITH scope so closures capture the right scope.
                        if (createScopeObjects
                                && node instanceof Scope
                                && node.getIntProp(Node.LET_FOR_LOOP_SCOPE, 0) == 1) {
                            Scope letScope = (Scope) node;
                            // Check if any initializer contains a function expression
                            if (hasClosureInInitializers(node)) {
                                node =
                                        visitLetScopeWithDeferredInit(
                                                parent, previous, node, letScope);
                                break;
                            }
                            // No closures, fall through to regular handling
                        }
                        Node child = node.getFirstChild();
                        if (child != null && child.getType() == Token.LET) {
                            // We have a let statement or expression rather than a
                            // let declaration
                            boolean createWith =
                                    tree.getType() != Token.FUNCTION
                                            || ((FunctionNode) tree).requiresActivation();
                            node = visitLet(createWith, parent, previous, node);
                            break;
                        }
                        // fall through to process let declaration...
                    }
                /* fall through */
                case Token.CONST:
                case Token.VAR:
                    {
                        // For for-in/for-of loop variables, skip initialization here.
                        // The variable stays in TDZ until assigned by the iterator.
                        // The TDZ scope is set up in wrapLoopBodyWithPerIterationScope.
                        if (node.getIntProp(Node.FOR_IN_OF_LOOP_VAR, 0) == 1) {
                            // Remove the declaration node - it will be handled by the TDZ scope
                            node = replaceCurrent(parent, previous, node, new Node(Token.EMPTY));
                            break;
                        }
                        Node result = new Node(Token.BLOCK);
                        for (Node cursor = node.getFirstChild(); cursor != null; ) {
                            // Move cursor to next before createAssignment gets chance
                            // to change n.next
                            Node n = cursor;
                            cursor = cursor.getNext();
                            if (n.getType() == Token.NAME) {
                                Node init = n.getFirstChild();
                                if (init != null) {
                                    n.removeChild(init);
                                } else if (type == Token.VAR) {
                                    // For var without initializer, skip (no TDZ for var)
                                    continue;
                                } else {
                                    // For let/const without initializer, assign undefined
                                    // to exit the TDZ. (const without initializer is a
                                    // syntax error, but we handle it here for robustness)
                                    init = new Node(Token.VOID, Node.newNumber(0.0));
                                }
                                n.setType(Token.BINDNAME);
                                int setType;
                                if (type == Token.CONST) {
                                    setType = Token.SETCONST;
                                } else if (type == Token.LET) {
                                    setType = Token.SETLETINIT;
                                } else {
                                    setType = Token.SETNAME;
                                }
                                n = new Node(setType, n, init);
                            } else if (n.getType() == Token.LETEXPR) {
                                // Destructuring assignment already transformed to a LETEXPR
                            } else if (n.getType() == Token.LOOP) {
                                // For a for-loop with let/const init, the LET wrapper scope
                                // contains both declarations and the loop body.
                                // The loop body will be processed recursively after this switch.
                                result.addChildToBack(n);
                                continue;
                            } else {
                                throw Kit.codeBug();
                            }
                            Node pop = new Node(Token.EXPR_VOID, n);
                            pop.setLineColumnNumber(node.getLineno(), node.getColumn());
                            result.addChildToBack(pop);
                        }
                        node = replaceCurrent(parent, previous, node, result);
                        break;
                    }

                case Token.TYPEOFNAME:
                    {
                        Scope defining = scope.getDefiningScope(node.getString());
                        if (defining != null) {
                            node.setScope(defining);
                        }
                    }
                    break;

                case Token.TYPEOF:
                case Token.IFNE:
                    {
                        /* We want to suppress warnings for undefined property o.p
                         * for the following constructs: typeof o.p, if (o.p),
                         * if (!o.p), if (o.p == undefined), if (undefined == o.p)
                         */
                        Node child = node.getFirstChild();
                        if (type == Token.IFNE) {
                            while (child.getType() == Token.NOT) {
                                child = child.getFirstChild();
                            }
                            if (child.getType() == Token.EQ || child.getType() == Token.NE) {
                                Node first = child.getFirstChild();
                                Node last = child.getLastChild();
                                if (first.getType() == Token.UNDEFINED) {
                                    child = last;
                                } else if (last.getType() == Token.UNDEFINED) {
                                    child = first;
                                }
                            }
                        }
                        if (child.getType() == Token.GETPROP) {
                            child.setType(Token.GETPROPNOWARN);
                        }
                        break;
                    }

                case Token.SETNAME:
                    if (inStrictMode) {
                        node.setType(Token.STRICT_SETNAME);
                        if (node.getFirstChild().getType() == Token.BINDNAME) {
                            Node name = node.getFirstChild();
                            if (name instanceof Name
                                    && "eval".equals(((Name) name).getIdentifier())) {
                                // Don't allow set of `eval` in strict mode
                                reportError("syntax error");
                            }
                        }
                    }
                /* fall through */
                case Token.NAME:
                case Token.SETCONST:
                case Token.SETLETINIT:
                case Token.DELPROP:
                    {
                        // Turn name to var for faster access if possible
                        if (createScopeObjects) {
                            break;
                        }
                        Node nameSource;
                        if (type == Token.NAME) {
                            nameSource = node;
                        } else {
                            nameSource = node.getFirstChild();
                            if (nameSource.getType() != Token.BINDNAME) {
                                if (type == Token.DELPROP) {
                                    break;
                                }
                                throw Kit.codeBug();
                            }
                        }
                        if (nameSource.getScope() != null) {
                            break; // already have a scope set
                        }
                        String name = nameSource.getString();
                        Scope defining = scope.getDefiningScope(name);
                        if (defining != null) {
                            nameSource.setScope(defining);
                            if (type == Token.NAME) {
                                node.setType(Token.GETVAR);
                            } else if (type == Token.SETNAME || type == Token.STRICT_SETNAME) {
                                node.setType(Token.SETVAR);
                                nameSource.setType(Token.STRING);
                            } else if (type == Token.SETCONST) {
                                node.setType(Token.SETCONSTVAR);
                                nameSource.setType(Token.STRING);
                            } else if (type == Token.SETLETINIT) {
                                node.setType(Token.SETLETVAR);
                                nameSource.setType(Token.STRING);
                            } else if (type == Token.DELPROP) {
                                // Local variables are by definition permanent
                                Node n = new Node(Token.FALSE);
                                node = replaceCurrent(parent, previous, node, n);
                            } else {
                                throw Kit.codeBug();
                            }
                        }
                        break;
                    }

                case Token.OBJECTLIT:
                    {
                        Object[] propertyIds = (Object[]) node.getProp(Node.OBJECT_IDS_PROP);
                        if (propertyIds != null) {
                            for (Object propertyId : propertyIds) {
                                if (!(propertyId instanceof Node)) continue;
                                transformCompilationUnit_r(
                                        tree,
                                        (Node) propertyId,
                                        node instanceof Scope ? (Scope) node : scope,
                                        createScopeObjects,
                                        inStrictMode);
                            }
                        }
                    }
            }

            transformCompilationUnit_r(
                    tree,
                    node,
                    node instanceof Scope ? (Scope) node : scope,
                    createScopeObjects,
                    inStrictMode);
        }
    }

    protected void visitNew(Node node, ScriptNode tree) {}

    protected void visitCall(Node node, ScriptNode tree) {}

    protected Node visitLet(boolean createWith, Node parent, Node previous, Node scopeNode) {
        Node vars = scopeNode.getFirstChild();
        Node body = vars.getNext();
        scopeNode.removeChild(vars);
        scopeNode.removeChild(body);
        boolean isExpression = scopeNode.getType() == Token.LETEXPR;
        Node result;
        Node newVars;
        if (createWith) {
            result = new Node(isExpression ? Token.WITHEXPR : Token.BLOCK);
            result = replaceCurrent(parent, previous, scopeNode, result);
            ArrayList<Object> list = new ArrayList<>();
            ArrayList<String> constNames = new ArrayList<>();
            Node objectLiteral = new Node(Token.OBJECTLIT);
            for (Node v = vars.getFirstChild(); v != null; v = v.getNext()) {
                Node current = v;
                if (current.getType() == Token.LETEXPR) {
                    // destructuring in let expr, e.g. let ([x, y] = [3, 4]) {}
                    List<?> destructuringNames =
                            (List<?>) current.getProp(Node.DESTRUCTURING_NAMES);
                    Node c = current.getFirstChild();
                    if (c.getType() != Token.LET) throw Kit.codeBug();
                    // Add initialization code to front of body
                    if (isExpression) {
                        body = new Node(Token.COMMA, c.getNext(), body);
                    } else {
                        body = new Node(Token.BLOCK, new Node(Token.EXPR_VOID, c.getNext()), body);
                    }
                    // Update "list" and "objectLiteral" for the variables
                    // defined in the destructuring assignment
                    if (destructuringNames != null) {
                        list.addAll(destructuringNames);
                        for (int i = 0; i < destructuringNames.size(); i++) {
                            // For destructuring, initialize to undefined - the actual values
                            // are set by the destructuring assignment that's added to the body.
                            // We don't use TDZ here because the destructuring assignment will
                            // run immediately after entering the WITH scope.
                            objectLiteral.addChildToBack(new Node(Token.VOID, Node.newNumber(0.0)));
                        }
                    }
                    current = c.getFirstChild(); // should be a NAME, checked below
                }
                if (current.getType() != Token.NAME) throw Kit.codeBug();
                String varName = current.getString();
                list.add(ScriptRuntime.getIndexObject(varName));
                // Check if this variable is a const declaration
                if (scopeNode instanceof Scope) {
                    Scope scope = (Scope) scopeNode;
                    org.mozilla.javascript.ast.Symbol sym = scope.getSymbol(varName);
                    if (sym != null && sym.getDeclType() == Token.CONST) {
                        constNames.add(varName);
                    }
                }
                Node init = current.getFirstChild();
                if (init != null) {
                    // Has an initializer (e.g., for loop: let i = 0)
                    // Use the initializer value directly
                    objectLiteral.addChildToBack(init);
                } else {
                    // No initializer - initialize to TDZ for temporal dead zone semantics.
                    // The actual initialization happens when the declaration is executed.
                    objectLiteral.addChildToBack(new Node(Token.TDZ));
                }
            }
            objectLiteral.putProp(Node.OBJECT_IDS_PROP, list.toArray());
            newVars = new Node(Token.ENTERWITH, objectLiteral);
            // Pass const variable names to runtime so they can be marked READONLY
            if (!constNames.isEmpty()) {
                newVars.putProp(Node.CONST_NAMES_PROP, constNames.toArray(new String[0]));
            }
            result.addChildToBack(newVars);
            result.addChildToBack(new Node(Token.WITH, body));
            result.addChildToBack(new Node(Token.LEAVEWITH));
        } else {
            result = new Node(isExpression ? Token.COMMA : Token.BLOCK);
            result = replaceCurrent(parent, previous, scopeNode, result);
            newVars = new Node(Token.COMMA);
            for (Node v = vars.getFirstChild(); v != null; v = v.getNext()) {
                Node current = v;
                if (current.getType() == Token.LETEXPR) {
                    // destructuring in let expr, e.g. let ([x, y] = [3, 4]) {}
                    Node c = current.getFirstChild();
                    if (c.getType() != Token.LET) throw Kit.codeBug();
                    // Add initialization code to front of body
                    if (isExpression) {
                        body = new Node(Token.COMMA, c.getNext(), body);
                    } else {
                        body = new Node(Token.BLOCK, new Node(Token.EXPR_VOID, c.getNext()), body);
                    }
                    // We're removing the LETEXPR, so move the symbols
                    Scope.joinScopes((Scope) current, (Scope) scopeNode);
                    current = c.getFirstChild(); // should be a NAME, checked below
                }
                if (current.getType() != Token.NAME) throw Kit.codeBug();
                Node stringNode = Node.newString(current.getString());
                stringNode.setScope((Scope) scopeNode);
                Node init = current.getFirstChild();
                if (init == null) {
                    init = new Node(Token.VOID, Node.newNumber(0.0));
                }
                newVars.addChildToBack(new Node(Token.SETVAR, stringNode, init));
            }
            if (isExpression) {
                result.addChildToBack(newVars);
                scopeNode.setType(Token.COMMA);
                result.addChildToBack(scopeNode);
                scopeNode.addChildToBack(body);
                if (body instanceof Scope) {
                    Scope scopeParent = ((Scope) body).getParentScope();
                    ((Scope) body).setParentScope((Scope) scopeNode);
                    ((Scope) scopeNode).setParentScope(scopeParent);
                }
            } else {
                result.addChildToBack(new Node(Token.EXPR_VOID, newVars));
                scopeNode.setType(Token.BLOCK);
                result.addChildToBack(scopeNode);
                scopeNode.addChildrenToBack(body);
                if (body instanceof Scope) {
                    Scope scopeParent = ((Scope) body).getParentScope();
                    ((Scope) body).setParentScope((Scope) scopeNode);
                    ((Scope) scopeNode).setParentScope(scopeParent);
                }
            }
        }
        return result;
    }

    /**
     * Transform a LET scope (from for-loop with let/const) into a WITH scope when
     * createScopeObjects is true. This ensures closures in the initializer capture the scope, and
     * marks const properties as READONLY.
     */
    private Node visitLetScopeWithConst(Node parent, Node previous, Node node, Scope letScope) {
        // Create WITH scope structure: BLOCK { ENTERWITH(obj); WITH { children }; LEAVEWITH }
        Node result = new Node(Token.BLOCK);
        result = replaceCurrent(parent, previous, node, result);

        // Create object literal with property values from NAME children
        ArrayList<Object> list = new ArrayList<>();
        ArrayList<String> constNames = new ArrayList<>();
        Node objectLiteral = new Node(Token.OBJECTLIT);

        // Collect NAME children with their initializers, and LOOP children for the body
        ArrayList<Node> bodyNodes = new ArrayList<>();
        for (Node child = node.getFirstChild(); child != null; ) {
            Node next = child.getNext();
            if (child.getType() == Token.NAME) {
                String varName = child.getString();
                list.add(ScriptRuntime.getIndexObject(varName));

                // Check if this is a const declaration
                org.mozilla.javascript.ast.Symbol sym = letScope.getSymbol(varName);
                if (sym != null && sym.getDeclType() == Token.CONST) {
                    constNames.add(varName);
                }

                Node init = child.getFirstChild();
                if (init != null) {
                    child.removeChild(init);
                    objectLiteral.addChildToBack(init);
                } else {
                    objectLiteral.addChildToBack(new Node(Token.TDZ));
                }
            } else {
                // LOOP or other body nodes
                node.removeChild(child);
                bodyNodes.add(child);
            }
            child = next;
        }

        objectLiteral.putProp(Node.OBJECT_IDS_PROP, list.toArray());
        Node enterWith = new Node(Token.ENTERWITH, objectLiteral);
        // Pass const names to runtime for READONLY marking
        if (!constNames.isEmpty()) {
            enterWith.putProp(Node.CONST_NAMES_PROP, constNames.toArray(new String[0]));
        }
        result.addChildToBack(enterWith);

        // Create body node containing the LOOP and other children
        Node body = new Node(Token.BLOCK);
        for (Node bodyNode : bodyNodes) {
            body.addChildToBack(bodyNode);
        }
        result.addChildToBack(new Node(Token.WITH, body));
        result.addChildToBack(new Node(Token.LEAVEWITH));

        // Clear the symbol table to prevent re-processing
        letScope.setSymbolTable(null);

        return result;
    }

    /**
     * Check if any NAME children (or grandchildren) have initializers containing function
     * expressions. This is used to determine if we need special handling for closures in for-loop
     * let initializers. The structure is: LET scope -> LET decl -> NAME nodes
     */
    private boolean hasClosureInInitializers(Node node) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child.getType() == Token.NAME) {
                Node init = child.getFirstChild();
                if (init != null && containsFunction(init)) {
                    return true;
                }
            } else if (child.getType() == Token.LET || child.getType() == Token.CONST) {
                // Check grandchildren (NAME nodes inside the LET/CONST declaration)
                for (Node grandchild = child.getFirstChild();
                        grandchild != null;
                        grandchild = grandchild.getNext()) {
                    if (grandchild.getType() == Token.NAME) {
                        Node init = grandchild.getFirstChild();
                        if (init != null && containsFunction(init)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Recursively check if a node or its descendants contain a function expression. */
    private boolean containsFunction(Node node) {
        if (node.getType() == Token.FUNCTION) {
            return true;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (containsFunction(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transform a LET scope (from for-loop with let) into a WITH scope with deferred
     * initialization. This ensures closures in the initializer capture the WITH scope (which
     * contains the let variables) rather than the outer scope.
     *
     * <p>Generated structure: BLOCK { ENTERWITH(obj with TDZ); WITH { inits; body }; LEAVEWITH }
     */
    private Node visitLetScopeWithDeferredInit(
            Node parent, Node previous, Node node, Scope letScope) {
        Node result = new Node(Token.BLOCK);
        result = replaceCurrent(parent, previous, node, result);

        // Create object literal with TDZ values for all variables
        // We'll assign the real values INSIDE the WITH scope
        ArrayList<Object> list = new ArrayList<>();
        Node objectLiteral = new Node(Token.OBJECTLIT);

        // Collect variable info and body nodes
        ArrayList<Node[]> varInits = new ArrayList<>(); // [nameNode, initNode] pairs
        ArrayList<Node> bodyNodes = new ArrayList<>();

        for (Node child = node.getFirstChild(); child != null; ) {
            Node next = child.getNext();
            if (child.getType() == Token.NAME) {
                String varName = child.getString();
                list.add(ScriptRuntime.getIndexObject(varName));

                // Start with TDZ in the object literal
                objectLiteral.addChildToBack(new Node(Token.TDZ));

                // Save the initializer for later assignment inside WITH scope
                Node init = child.getFirstChild();
                if (init != null) {
                    child.removeChild(init);
                    varInits.add(new Node[] {child, init});
                }
            } else if (child.getType() == Token.LET || child.getType() == Token.CONST) {
                // Handle the nested LET/CONST declaration node
                // Its children are the actual NAME nodes with initializers
                for (Node grandchild = child.getFirstChild();
                        grandchild != null;
                        grandchild = grandchild.getNext()) {
                    if (grandchild.getType() == Token.NAME) {
                        String varName = grandchild.getString();
                        list.add(ScriptRuntime.getIndexObject(varName));

                        // Start with TDZ in the object literal
                        objectLiteral.addChildToBack(new Node(Token.TDZ));

                        // Save the initializer for later assignment inside WITH scope
                        Node init = grandchild.getFirstChild();
                        if (init != null) {
                            grandchild.removeChild(init);
                            varInits.add(new Node[] {grandchild, init});
                        }
                    }
                }
                // Remove the LET/CONST node - we've extracted what we need
                node.removeChild(child);
            } else {
                // LOOP or other body nodes
                node.removeChild(child);
                bodyNodes.add(child);
            }
            child = next;
        }

        objectLiteral.putProp(Node.OBJECT_IDS_PROP, list.toArray());
        Node enterWith = new Node(Token.ENTERWITH, objectLiteral);
        result.addChildToBack(enterWith);

        // Create body node - first the initializer assignments, then the rest
        Node body = new Node(Token.BLOCK);

        // Generate assignments for initializers INSIDE the WITH scope
        // This ensures closures capture the WITH scope
        for (Node[] varInit : varInits) {
            Node nameNode = varInit[0];
            Node initExpr = varInit[1];
            String varName = nameNode.getString();

            // Create: varName = initExpr (as SETLETINIT since we're initializing a let variable)
            // SETLETINIT bypasses TDZ checks because we're doing the initialization
            Node bindName = Node.newString(Token.BINDNAME, varName);
            Node assignment = new Node(Token.SETLETINIT, bindName, initExpr);
            Node exprStmt = new Node(Token.EXPR_VOID, assignment);
            body.addChildToBack(exprStmt);
        }

        // Add the rest of the body (LOOP, etc.)
        for (Node bodyNode : bodyNodes) {
            body.addChildToBack(bodyNode);
        }

        result.addChildToBack(new Node(Token.WITH, body));
        result.addChildToBack(new Node(Token.LEAVEWITH));

        // Clear the symbol table to prevent re-processing
        letScope.setSymbolTable(null);

        return result;
    }

    private static Node addBeforeCurrent(Node parent, Node previous, Node current, Node toAdd) {
        if (previous == null) {
            if (!(current == parent.getFirstChild())) Kit.codeBug();
            parent.addChildToFront(toAdd);
        } else {
            if (!(current == previous.getNext())) Kit.codeBug();
            parent.addChildAfter(toAdd, previous);
        }
        return toAdd;
    }

    private static Node replaceCurrent(Node parent, Node previous, Node current, Node replacement) {
        if (previous == null) {
            if (!(current == parent.getFirstChild())) Kit.codeBug();
            parent.replaceChild(current, replacement);
        } else if (previous.next == current) {
            // Check cachedPrev.next == current is necessary due to possible
            // tree mutations
            parent.replaceChildAfter(previous, replacement);
        } else {
            parent.replaceChild(current, replacement);
        }
        return replacement;
    }

    /**
     * Wrap the body portion of a loop with per-iteration scope for ES6 let semantics. This ensures
     * that closures created inside the loop capture a fresh binding for each iteration.
     */
    private void wrapLoopBodyWithPerIterationScope(Node loop, List<String> varNames) {
        // ES6 per-iteration bindings for let in loops.
        //
        // There are two types of loops:
        // 1. Regular FOR loops: 4 TARGETs (body, incr, cond, break)
        // 2. FOR-IN/FOR-OF loops: 3 TARGETs (body, cond, break)
        //
        // For regular FOR loops with 4 TARGETs, we need special handling per ES6 spec:
        // - CreatePerIterationEnvironment is called before first test and after each body
        // - Test and body share one environment
        // - Increment runs in the NEXT environment
        //
        // For FOR-IN/FOR-OF loops, we just wrap the body since each iteration
        // naturally gets a fresh value assigned.

        // Count targets and find key nodes
        Node gotoNode = null;
        Node bodyTarget = null;
        Node ifeqNode = null;
        Node[] targets = new Node[4];
        int targetCount = 0;

        for (Node child = loop.getFirstChild(); child != null; child = child.getNext()) {
            if (child.getType() == Token.GOTO && gotoNode == null) {
                gotoNode = child;
            } else if (child.getType() == Token.TARGET) {
                if (targetCount < 4) {
                    targets[targetCount] = child;
                }
                targetCount++;
                if (targetCount == 1) {
                    bodyTarget = child;
                }
            } else if (child.getType() == Token.IFEQ) {
                ifeqNode = child;
            }
        }

        if (gotoNode == null || bodyTarget == null || ifeqNode == null) {
            return; // Can't find basic loop structure
        }

        if (targetCount == 4) {
            // Regular FOR loop with increment: use ES6-spec transformation
            wrapForLoopWithPerIterationScope(
                    loop, varNames, gotoNode, bodyTarget, targets[1], targets[3], ifeqNode);
        } else if (targetCount == 3) {
            // FOR-IN/FOR-OF loop: use simple body wrapping
            wrapLoopBodyOnly(loop, varNames, bodyTarget, targets[1]);
        }
        // Other structures not supported
    }

    private void wrapForLoopWithPerIterationScope(
            Node loop,
            List<String> varNames,
            Node gotoNode,
            Node bodyTarget,
            Node incrTarget,
            Node breakTarget,
            Node ifeqNode) {
        // Transform for regular FOR loops:
        // FROM: LOOP { GOTO, TARGET(body), body, TARGET(incr), incr, EMPTY,
        //              TARGET(cond), IFEQ, TARGET(break) }
        // TO:   LOOP { ENTERWITH, GOTO, TARGET(body), body, SWITCH_PER_ITER_SCOPE,
        //              TARGET(incr), incr, EMPTY, TARGET(cond), IFEQ, LEAVEWITH, TARGET(break) }

        // 1. Create ENTERWITH at the start of the loop
        Node objectLiteral = createPerIterObjectLiteral(varNames);
        Node enterWith = new Node(Token.ENTERWITH, objectLiteral);
        Node firstChild = loop.getFirstChild();
        if (firstChild != null) {
            loop.addChildBefore(enterWith, firstChild);
        } else {
            loop.addChildToFront(enterWith);
        }

        // 2. Create SWITCH_PER_ITER_SCOPE between body and incrTarget
        Node switchNode = new Node(Token.SWITCH_PER_ITER_SCOPE);
        switchNode.putProp(Node.PER_ITERATION_NAMES_PROP, varNames.toArray(new String[0]));
        loop.addChildBefore(switchNode, incrTarget);

        // 3. Create LEAVEWITH before breakTarget
        Node leaveWith = new Node(Token.LEAVEWITH);
        loop.addChildBefore(leaveWith, breakTarget);
    }

    private void wrapLoopBodyOnly(
            Node loop, List<String> varNames, Node bodyTarget, Node afterBody) {
        // Wrap loop body with ENTERWITH/WITH/LEAVEWITH for for-in/for-of loops.
        // Find body nodes (between bodyTarget and afterBody)
        List<Node> bodyNodes = new ArrayList<>();
        for (Node n = bodyTarget.getNext(); n != null && n != afterBody; n = n.getNext()) {
            bodyNodes.add(n);
        }

        if (bodyNodes.isEmpty()) {
            return;
        }

        // Remove body nodes from loop
        for (Node n : bodyNodes) {
            loop.removeChild(n);
        }

        // Create object literal for WITH scope with TDZ values.
        // For for-in/for-of loops, we initialize with TDZ_VALUE because the variable
        // is in TDZ until assigned by the iterator. The SETLETINIT in the body will
        // assign the iterator value and exit the TDZ.
        Node objectLiteral = createPerIterTdzObjectLiteral(varNames);

        // Create the per-iteration scope structure
        Node enterWith = new Node(Token.ENTERWITH, objectLiteral);
        Node withBody = new Node(Token.BLOCK);
        for (Node n : bodyNodes) {
            withBody.addChildToBack(n);
        }
        // Note: Unlike regular for loops, for-in/for-of loops don't need COPY_PER_ITER_SCOPE
        // because each iteration gets a fresh value from the iterator. The per-iteration
        // scope is discarded at the end of each iteration and doesn't need to be copied back.

        Node withNode = new Node(Token.WITH, withBody);
        withNode.putProp(Node.PER_ITERATION_NAMES_PROP, varNames);

        Node leaveWith = new Node(Token.LEAVEWITH);

        // Create wrapper block
        Node wrapper = new Node(Token.BLOCK);
        wrapper.addChildToBack(enterWith);
        wrapper.addChildToBack(withNode);
        wrapper.addChildToBack(leaveWith);

        // Insert wrapper after bodyTarget
        loop.addChildAfter(wrapper, bodyTarget);
    }

    private Node createPerIterObjectLiteral(List<String> varNames) {
        Node objectLiteral = new Node(Token.OBJECTLIT);
        ArrayList<Object> propIds = new ArrayList<>();
        for (String name : varNames) {
            propIds.add(ScriptRuntime.getIndexObject(name));
            objectLiteral.addChildToBack(Node.newString(Token.NAME, name));
        }
        objectLiteral.putProp(Node.OBJECT_IDS_PROP, propIds.toArray());
        return objectLiteral;
    }

    /**
     * Creates an object literal for per-iteration scope where each property is initialized to
     * TDZ_VALUE. This is used for for-in/for-of loops where the variable starts in TDZ until
     * assigned by the iterator.
     */
    private Node createPerIterTdzObjectLiteral(List<String> varNames) {
        Node objectLiteral = new Node(Token.OBJECTLIT);
        ArrayList<Object> propIds = new ArrayList<>();
        for (String name : varNames) {
            propIds.add(ScriptRuntime.getIndexObject(name));
            // Use TDZ token instead of NAME lookup to avoid TDZ error during object creation
            objectLiteral.addChildToBack(new Node(Token.TDZ));
        }
        objectLiteral.putProp(Node.OBJECT_IDS_PROP, propIds.toArray());
        return objectLiteral;
    }

    private Deque<Node> loops;
    private Deque<Node> loopEnds;
    private boolean hasFinally;
}
