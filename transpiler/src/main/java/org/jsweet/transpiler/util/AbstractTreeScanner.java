/* 
 * JSweet transpiler - http://www.jsweet.org
 * Copyright (C) 2015 CINCHEO SAS <renaud.pawlak@cincheo.fr>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.jsweet.transpiler.util;

import java.io.File;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.function.Consumer;

import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.apache.commons.io.FileUtils;
import org.jsweet.transpiler.JSweetContext;
import org.jsweet.transpiler.JSweetProblem;
import org.jsweet.transpiler.SourcePosition;
import org.jsweet.transpiler.TranspilationHandler;
import org.jsweet.transpiler.model.ExtendedElement;
import org.jsweet.transpiler.model.ExtendedElementFactory;
import org.jsweet.transpiler.model.support.ExtendedElementSupport;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

/**
 * A Java AST scanner for JSweet.
 * 
 * @author Renaud Pawlak
 * @author Louis Grignon
 */
public abstract class AbstractTreeScanner extends TreeScanner<Void, Trees> {

	private TranspilationHandler logHandler;

	/**
	 * Report a JSweet problem on the given program element (tree).
	 * 
	 * @param tree    the program element causing the problem
	 * @param problem the problem to be reported
	 * @param params  problem arguments
	 */
	public void report(Tree tree, JSweetProblem problem, Object... params) {
		report(tree, null, problem, params);
	}

	/**
	 * Report a JSweet problem on the given named program element (tree).
	 * 
	 * @param tree
	 *            the program element causing the problem
	 * @param name
	 *            the name of that program element
	 * @param problem
	 *            the problem to be reported
	 * @param params
	 *            problem arguments
	 */
	public void report(Tree tree, Name name, JSweetProblem problem, Object... params) {
		if (logHandler == null) {
			System.err.println(problem.getMessage(params));
		} else {
			if (compilationUnit == null) {
				logHandler.report(problem, null, problem.getMessage(params));
			} else {
				SourcePosition sourcePosition = util().getSourcePosition(compilationUnit, tree, name);
				logHandler.report(problem, sourcePosition, problem.getMessage(params));
			}
		}
	}

	/**
	 * The scanning stack.
	 */
	protected Stack<Tree> stack = new Stack<Tree>();

	/**
	 * A map holding static import statements.
	 */
	protected Map<String, ImportTree> staticImports = new HashMap<>();

	/**
	 * Gets the map of static imports in the current compilation unit.
	 * 
	 * @see #getCompilationUnit()
	 */
	public Map<String, ImportTree> getStaticImports() {
		return staticImports;
	}

	/**
	 * Holds the compilation being currently scanned.
	 */
	protected CompilationUnitTree compilationUnit;

	/**
	 * Gets the currently scanned compilation unit.
	 */
	public CompilationUnitTree getCompilationUnit() {
		return compilationUnit;
	}

	protected final JSweetContext context;

	/**
	 * Gets the transpiler context.
	 */
	public JSweetContext getContext() {
		return context;
	}

	protected SourcePositions sourcePositions() {
		return trees().getSourcePositions();
	}

	protected Trees trees() {
		return getContext().trees;
	}

	protected Types types() {
		return getContext().types;
	}

	protected Elements elements() {
		return getContext().elements;
	}

	protected Util util() {
		return getContext().util;
	}

	private Entry<String, String[]> sourceCache;

	/**
	 * Gets the Java source code for the given compilation unit.
	 */
	protected String[] getGetSource(CompilationUnitTree compilationUnit) {
		if (sourceCache != null && sourceCache.getKey().equals(compilationUnit.getSourceFile().getName())) {
			return sourceCache.getValue();
		} else {
			try {
				sourceCache = new AbstractMap.SimpleEntry<>(compilationUnit.getSourceFile().getName(),
						FileUtils.readFileToString(new File(compilationUnit.getSourceFile().getName())).split("\\n"));
			} catch (Exception e) {
				return null;
			}
			return sourceCache.getValue();
		}
	}

	/**
	 * Creates a new scanner.
	 * 
	 * @param logHandler      the handler for reporting messages
	 * @param context         the JSweet transpilation context
	 * @param compilationUnit the compilation to be scanned
	 */
	protected AbstractTreeScanner(TranspilationHandler logHandler, JSweetContext context,
			CompilationUnitTree compilationUnit) {
		this.logHandler = logHandler;
		this.context = context;
		this.setCompilationUnit(compilationUnit);
	}

	/**
	 * Sets the compilation unit to be scanned.
	 */
	protected final void setCompilationUnit(CompilationUnitTree compilationUnit) {
		if (compilationUnit != null) {
			this.compilationUnit = compilationUnit;
			for (ImportTree importTree : this.compilationUnit.getImports()) {
				if (importTree.isStatic()) {
					String importedIdentifier = importTree.getQualifiedIdentifier().toString();
					staticImports.put(importedIdentifier.substring(importedIdentifier.lastIndexOf(".") + 1),
							importTree);
				}
			}
		} else {
			this.compilationUnit = null;
		}
	}

	/**
	 * Generically scan an extended element.
	 */
	@SuppressWarnings("rawtypes")
	public void scan(ExtendedElement element) {
		scan(((ExtendedElementSupport) element).getTree(), trees());
	}

	/**
	 * Scans a program tree.
	 */
	@Override
	public Void scan(Tree tree, Trees trees) {
		if (tree == null) {
			return null;
		}
		enter(tree);
		try {
			tree.accept(this, trees);
		} catch (RollbackException rollback) {
			if (rollback.getTarget() == tree) {
				onRollbacked(tree);
				if (rollback.getOnRollbacked() != null) {
					rollback.getOnRollbacked().accept(tree);
				}
			} else {
				throw rollback;
			}
		} catch (Exception e) {
			report(tree, JSweetProblem.INTERNAL_TRANSPILER_ERROR);
			dumpStackTrace();
			e.printStackTrace();
		} finally {
			exit();
		}

		return null;
	}

	/**
	 * Pretty prints the current scanning trace.
	 * 
	 * <p>
	 * This is useful for reporting internal errors and give information about what
	 * happened to the user.
	 */
	protected void dumpStackTrace() {
		System.err.println("dumping transpiler's strack trace:");
		for (int i = stack.size() - 1; i >= 0; i--) {
			Tree tree = stack.get(i);
			if (tree == null) {
				continue;
			}
			String str = tree.toString().trim();
			int intialLength = str.length();
			int index = str.indexOf('\n');
			if (index > 0) {
				str = str.substring(0, index + 1);
			}
			str = str.replace('\n', ' ');
			str = str.substring(0, Math.min(str.length() - 1, 30));
			System.err.print("   [" + stack.get(i).getClass().getSimpleName() + "] " + str
					+ (str.length() < intialLength ? "..." : "") + " ("
					+ util().getSourcePosition(compilationUnit, tree));
			System.err.println(")");
		}
	}

	/**
	 * A global handler to be called when a rollback operation terminates.
	 * 
	 * @param target the rollback's target
	 * @see #rollback(Tree, Consumer)
	 */
	protected void onRollbacked(Tree target) {
	}

	/**
	 * Rollbacks (undo) the current printing transaction up to the target.
	 * 
	 * @param target       the element up to which the printing should be undone
	 * @param onRollbacked the callback to be invoked once the rollback is finished
	 *                     (see also {@link #onRollbacked(Tree)}
	 */
	protected void rollback(Tree target, Consumer<Tree> onRollbacked) {
		throw new RollbackException(target, onRollbacked);
	}

	/**
	 * Enters a tree element (to be scanned for printing).
	 * 
	 * @see #exit()
	 */
	protected void enter(Tree tree) {
		stack.push(tree);
	}

	/**
	 * Exits a tree element (to be scanned for printing).
	 * 
	 * @see #enter(Tree)
	 */
	protected void exit() {
		stack.pop();
	}

	/**
	 * Returns the printer's scanning stack.
	 */
	public Stack<Tree> getStack() {
		return this.stack;
	}

	/**
	 * Returns the currently visited node in the printer's scanning stack.
	 */
	public Tree getCurrent() {
		if (this.stack.size() >= 1) {
			return this.stack.get(this.stack.size() - 1);
		} else {
			return null;
		}
	}

	/**
	 * Returns the immediate parent in the printer's scanning stack.
	 */
	public Tree getParent() {
		if (this.stack.size() >= 2) {
			return this.stack.get(this.stack.size() - 2);
		} else {
			return null;
		}
	}

	/**
	 * Returns the parent of the immediate parent in the printer's scanning stack.
	 * 
	 * @see #getStack()
	 */
	public Tree getParentOfParent() {
		if (this.stack.size() >= 3) {
			return this.stack.get(this.stack.size() - 3);
		} else {
			return null;
		}
	}

	/**
	 * Gets the parent element in the printer's scanning stack.
	 * 
	 * @see #getStack()
	 */
	public ExtendedElement getParentElement() {
		return ExtendedElementFactory.INSTANCE.create(compilationUnit, getParent(), context);
	}

	/**
	 * Gets the first parent in the scanning stack matching the given type.
	 * 
	 * @param type the type to search for
	 * @return the first matching tree
	 * @see #getStack()
	 */
	@SuppressWarnings("unchecked")
	public <T extends Tree> T getParent(Class<T> type) {
		for (int i = this.stack.size() - 2; i >= 0; i--) {
			if (type.isAssignableFrom(this.stack.get(i).getClass())) {
				return (T) this.stack.get(i);
			}
		}
		return null;
	}

	/**
	 * Gets the first parent in the scanning stack matching the given element type.
	 * 
	 * @param elementType the type to search for
	 * @return the first matching element
	 * @see #getStack()
	 */
	@SuppressWarnings("unchecked")
	public <T extends Element> T getParentElement(Class<T> elementType) {
		for (int i = this.stack.size() - 2; i >= 0; i--) {
			Tree treeAtIndex = this.stack.get(i);
			Element element = trees().getElement(trees().getPath(compilationUnit, treeAtIndex));
			if (elementType.isAssignableFrom(element.getClass())) {
				return (T) element;
			}
		}
		return null;
	}

	/**
	 * Gets the first tree in the scanning stack that matched one of the given tree
	 * types.
	 * 
	 * @param types the tree types to be checked
	 * @return the first matching tree
	 * @see #getStack()
	 */
	public Tree getFirstParent(Class<?>... types) {
		for (int i = this.stack.size() - 2; i >= 0; i--) {
			for (Class<?> type : types) {
				if (type.isAssignableFrom(this.stack.get(i).getClass())) {
					return this.stack.get(i);
				}
			}
		}
		return null;
	}

	/**
	 * Gets the first parent matching the given type, looking up from the given tree
	 * in the scanning stack.
	 * 
	 * @param type the type to search for
	 * @param from the tree to start the search from
	 * @return the first matching parent in the scanning stack
	 * @see #getStack()
	 */
	@SuppressWarnings("unchecked")
	public <T extends Tree> T getParent(Class<T> type, Tree from) {
		for (int i = this.stack.size() - 1; i >= 0; i--) {
			if (this.stack.get(i) == from) {
				for (int j = i - 1; j >= 0; j--) {
					if (type.isAssignableFrom(this.stack.get(j).getClass())) {
						return (T) this.stack.get(j);
					}
				}
				return null;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	protected <T extends Element> T toElement(Tree tree) {
		return (T) context.trees.getElement(context.trees.getPath(getCompilationUnit(), tree));
	}

}
