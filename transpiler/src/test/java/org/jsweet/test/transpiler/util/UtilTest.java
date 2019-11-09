package org.jsweet.test.transpiler.util;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.apache.commons.lang3.tuple.Pair;
import org.jsweet.test.transpiler.AbstractTest;
import org.jsweet.transpiler.JSweetContext;
import org.jsweet.transpiler.SourceFile;
import org.jsweet.transpiler.util.ConsoleTranspilationHandler;
import org.jsweet.transpiler.util.ErrorCountTranspilationHandler;
import org.jsweet.transpiler.util.Util;
import org.junit.Before;
import org.junit.Test;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;

import source.structural.ExtendsClassInSameFile;

public class UtilTest extends AbstractTest {

	private JSweetContext context;
	private Util util;

	@Before
	public void setUp() throws Exception {
		URL testSourcesDirURL = getClass().getClassLoader().getResource("UtilTest-sources");
		File utilTestSourcesDir = new File(testSourcesDirURL.toURI());

		List<File> utilTestSourcesFiles = asList( //
				new File(utilTestSourcesDir, "TestNoPackage.java"), //
				new File(utilTestSourcesDir, "first/Test.java"), //
				new File(utilTestSourcesDir, "first/second/Test2.java"), //
				new File(utilTestSourcesDir, "first/second/third/Test3.java"));

		transpilerTest().getTranspiler().prepareForJavaFiles(utilTestSourcesFiles,
				new ErrorCountTranspilationHandler(new ConsoleTranspilationHandler()));
		context = transpilerTest().getTranspiler().getContext();
		util = new Util(context);
	}

	@Test
	public void testConvertToRelativePath() {
		assertEquals("../c", util.getRelativePath("/a/b", "/a/c"));
		assertEquals("..", util.getRelativePath("/a/b", "/a"));
		assertEquals("../e", util.getRelativePath("/a/b/c", "/a/b/e"));
		assertEquals("d", util.getRelativePath("/a/b/c", "/a/b/c/d"));
		assertEquals("d/e", util.getRelativePath("/a/b/c", "/a/b/c/d/e"));
		assertEquals("../../../d/e/f", util.getRelativePath("/a/b/c", "/d/e/f"));
		assertEquals("../..", util.getRelativePath("/a/b/c", "/a"));
		assertEquals("..", util.getRelativePath("/a/b/c", "/a/b"));
	}

	@Test
	public void testIsCoreType() throws Exception {
		TypeMirror stringType = util.getType(String.class);
		TypeMirror floatType = util.getType(float.class);
		assertTrue(util.isCoreType(stringType));
		assertTrue(util.isCoreType(floatType));
	}

	@Test
	public void testIsStringType() throws Exception {
		TypeMirror stringType = util.getType(String.class);
		assertTrue(util.isStringType(stringType));
	}


	@Test
	public void testIsStringTypeOnOtherClass() throws Exception {
		TypeMirror stringType = util.getType(Object.class);
		assertFalse(util.isStringType(stringType));
	}

	@Test
	public void testIsVoidType() throws Exception {
		TypeMirror type = util.getType(Void.class);
		assertTrue(util.isVoidType(type));
	}

	@Test
	public void testIsVoidTypeOnOtherClass() throws Exception {
		TypeMirror type = util.getType(int.class);
		assertFalse(util.isVoidType(type));
	}

	@Test
	public void testIsDeclarationOrSubClassDeclaration() throws Exception {

		SourceFile sourceFile = getSourceFile(ExtendsClassInSameFile.class);
		Pair<CompilationUnitTree, ClassTree> classDeclarationWithCompilUnit = getSourcePublicClassDeclaration(
				sourceFile);
		TypeMirror classType = getClassType(classDeclarationWithCompilUnit);

		boolean isDeclaration;
		String searchedClassName = ExtendsClassInSameFile.class.getName();

		logger.info("search class name: " + searchedClassName);
		isDeclaration = util.isDeclarationOrSubClassDeclaration(classType, searchedClassName);
		assertTrue(isDeclaration);

		searchedClassName = ExtendsClassInSameFile.class.getName().replace(".ExtendsClassInSameFile", ".Foo1");
		logger.info("search class name: " + searchedClassName);
		isDeclaration = util.isDeclarationOrSubClassDeclaration(classType, searchedClassName);
		assertTrue(isDeclaration);

		searchedClassName = ExtendsClassInSameFile.class.getName().replace(".ExtendsClassInSameFile", ".TRULULU");
		logger.info("search class name: " + searchedClassName);
		isDeclaration = util.isDeclarationOrSubClassDeclaration(classType, searchedClassName);
		assertFalse(isDeclaration);
	}

	private TypeMirror getClassType(Pair<CompilationUnitTree, ClassTree> classDeclarationWithCompilUnit) {
		TypeMirror classType = trees().getElement(
				trees().getPath(classDeclarationWithCompilUnit.getKey(), classDeclarationWithCompilUnit.getRight()))
				.asType();
		return classType;
	}

	@Test
	public void testIsDeclarationOrSubClassDeclarationBySimpleName() throws Exception {

		SourceFile sourceFile = getSourceFile(ExtendsClassInSameFile.class);
		Pair<CompilationUnitTree, ClassTree> classDeclarationWithCompilUnit = getSourcePublicClassDeclaration(
				sourceFile);
		TypeMirror classType = getClassType(classDeclarationWithCompilUnit);

		boolean isDeclaration;

		String searchedClassName = "ExtendsClassInSameFile";
		logger.info("search class name: " + searchedClassName);
		isDeclaration = util.isDeclarationOrSubClassDeclarationBySimpleName(classType, searchedClassName);
		assertTrue(isDeclaration);

		searchedClassName = "Foo1";
		logger.info("search class name: " + searchedClassName);
		isDeclaration = util.isDeclarationOrSubClassDeclarationBySimpleName(classType, searchedClassName);
		assertTrue(isDeclaration);

		searchedClassName = "TRULULU";
		logger.info("search class name: " + searchedClassName);
		isDeclaration = util.isDeclarationOrSubClassDeclarationBySimpleName(classType, searchedClassName);
		assertFalse(isDeclaration);
	}

	@Test
	public void getParentPackageOnNull() throws Exception {
		PackageElement parent = util.getParentPackage(null);
		assertNull(parent);
	}

	@Test
	public void getParentPackageOnRoot() throws Exception {
		PackageElement packageElement = context.elements.getPackageElement("first");

		PackageElement parent = util.getParentPackage(packageElement);
		assertNull(parent);
	}

	@Test
	public void getParentPackageOnChildPackage() throws Exception {
		PackageElement packageElement = context.elements.getPackageElement("first.second");

		PackageElement parent = util.getParentPackage(packageElement);
		assertEquals(context.elements.getPackageElement("first"), parent);
	}

	@Test
	public void getParentPackageOnThirdLevelPackage() throws Exception {
		PackageElement packageElement = context.elements.getPackageElement("first.second.third");

		PackageElement parent = util.getParentPackage(packageElement);
		assertEquals(context.elements.getPackageElement("first.second"), parent);
	}

	@Test
	public void getQualifiedNameForElementOnNull() throws Exception {
		Element element = null;
		String qualifiedName = util.getQualifiedName(element);
		assertNull(qualifiedName);
	}

	@Test
	public void getQualifiedNameForPackageElement() throws Exception {
		Element element = context.elements.getPackageElement("first.second.third");
		String qualifiedName = util.getQualifiedName(element);
		assertEquals("first.second.third", qualifiedName);
	}

	@Test
	public void getQualifiedNameForTypeElement() throws Exception {
		Element element = context.elements.getTypeElement("first.second.Test2");
		String qualifiedName = util.getQualifiedName(element);
		assertEquals("first.second.Test2", qualifiedName);
	}

	@Test
	public void getQualifiedNameForRootTypeElement() throws Exception {
		Element element = context.elements.getTypeElement("TestNoPackage");
		String qualifiedName = util.getQualifiedName(element);
		assertEquals("TestNoPackage", qualifiedName);
	}

	@Test
	public void getQualifiedNameForMethodElement() throws Exception {
		TypeElement typeElement = context.elements.getTypeElement("first.Test");
		Element element = typeElement.getEnclosedElements().stream()
				.filter(memberElement -> memberElement.getSimpleName().toString().equals("method1")).findFirst().get();
		String qualifiedName = util.getQualifiedName(element);
		assertEquals("first.Test.method1", qualifiedName);
	}

	@Test
	public void getQualifiedNameForFieldElement() throws Exception {
		TypeElement typeElement = context.elements.getTypeElement("first.second.Test2");
		Element element = typeElement.getEnclosedElements().stream()
				.filter(memberElement -> memberElement.getSimpleName().toString().equals("field1")).findFirst().get();
		String qualifiedName = util.getQualifiedName(element);
		assertEquals("first.second.Test2.field1", qualifiedName);
	}

	@Test
	public void getQualifiedNameForMethodParamElement() throws Exception {
		TypeElement typeElement = context.elements.getTypeElement("first.Test");
		Element element = typeElement.getEnclosedElements().stream() //
				.filter(memberElement -> memberElement.getSimpleName().toString().equals("method1")) //
				.map(methodElement -> (ExecutableElement) methodElement).findFirst().get() //
				.getParameters().get(0);
		String qualifiedName = util.getQualifiedName(element);
		assertEquals("first.Test.method1.param1", qualifiedName);
	}

	@Test
	public void getQualifiedNameForVariableElement() throws Exception {
		Element varElement = getVariableElement();
		String qualifiedName = util.getQualifiedName(varElement);
		assertEquals("first.Test.method1.var1", qualifiedName);
	}

	@Test
	public void getOperatorTypeOnNull() throws Exception {
		TypeMirror operatorType = util.getOperatorType(null);

		assertNull(operatorType);
	}

	@Test
	public void getOperatorTypeOnInt() throws Exception {
		MethodTree testMethod = getMethodTree("plusInt");
		BinaryTree binaryTree = extractBinaryFromSingleLineTestMethod(testMethod);

		TypeMirror operatorType = util.getOperatorType(binaryTree);

		assertEquals(util.getType(int.class), operatorType);
	}

	@Test
	public void getOperatorTypeOnDouble() throws Exception {
		MethodTree testMethod = getMethodTree("minusDouble");
		BinaryTree binaryTree = extractBinaryFromSingleLineTestMethod(testMethod);

		TypeMirror operatorType = util.getOperatorType(binaryTree);

		assertEquals(util.getType(double.class), operatorType);
	}

	@Test
	public void getOperatorTypeOnString() throws Exception {
		MethodTree testMethod = getMethodTree("plusString");
		BinaryTree binaryTree = extractBinaryFromSingleLineTestMethod(testMethod);

		TypeMirror operatorType = util.getOperatorType(binaryTree);

		assertEquals(util.getType(String.class), operatorType);
	}

	@Test
	public void getOperatorTypeOnStringPlusInt() throws Exception {
		MethodTree testMethod = getMethodTree("plusStringInt");
		BinaryTree binaryTree = extractBinaryFromSingleLineTestMethod(testMethod);

		TypeMirror operatorType = util.getOperatorType(binaryTree);

		assertEquals(util.getType(String.class), operatorType);
	}

	private CompilationUnitTree getMainCompilationUnit() {
		return getCompilationUnitForClassName("Test");
	}

	private CompilationUnitTree getCompilationUnitForClassName(String classSimpleName) {
		return context.compilationUnits.stream() //
				.filter(compilUnit -> compilUnit.getTypeDecls().stream().anyMatch(
						typeDecl -> ((ClassTree) typeDecl).getSimpleName().toString().equals(classSimpleName)))
				.findFirst() //
				.orElse(null);
	}

	private MethodTree getMethodTree(String methodName) {
		return getMethodTree(getMainCompilationUnit(), methodName);
	}

	private MethodTree getMethodTree(CompilationUnitTree compilationUnit, String methodName) {
		ClassTree firstType = (ClassTree) compilationUnit.getTypeDecls().get(0);
		MethodTree method = (MethodTree) firstType.getMembers().stream()
				.filter(memberTree -> memberTree instanceof MethodTree
						&& ((MethodTree) memberTree).getName().toString().equals(methodName))
				.findFirst().orElse(null);
		return method;
	}

	private Element getVariableElement() {
		CompilationUnitTree compilationUnit = getMainCompilationUnit();
		MethodTree method = getMethodTree("method1");
		VariableTree var = (VariableTree) method.getBody().getStatements().get(0);

		Element varElement = trees().getElement(trees().getPath(compilationUnit, var));
		return varElement;
	}

	private BinaryTree extractBinaryFromSingleLineTestMethod(MethodTree method) {
		VariableTree var = (VariableTree) method.getBody().getStatements().get(0);
		return (BinaryTree) var.getInitializer();
	}

	private LambdaExpressionTree extractLambdaFromSingleLineTestMethod(MethodTree method) {
		VariableTree var = (VariableTree) method.getBody().getStatements().get(0);
		return (LambdaExpressionTree) var.getInitializer();
	}
}
