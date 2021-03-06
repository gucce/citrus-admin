/*
 * Copyright 2006-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.admin.service;

import com.consol.citrus.TestCase;
import com.consol.citrus.admin.converter.action.ActionConverter;
import com.consol.citrus.admin.converter.action.TestActionConverter;
import com.consol.citrus.admin.exception.ApplicationRuntimeException;
import com.consol.citrus.admin.marshal.XmlTestMarshaller;
import com.consol.citrus.admin.mock.Mocks;
import com.consol.citrus.admin.model.*;
import com.consol.citrus.admin.model.spring.SpringBeans;
import com.consol.citrus.dsl.actions.DelegatingTestAction;
import com.consol.citrus.dsl.simulation.TestSimulator;
import com.consol.citrus.model.testcase.core.*;
import com.consol.citrus.util.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jboss.shrinkwrap.resolver.api.NoResolvedResultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.xml.transform.StringSource;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Christoph Deppisch
 */
@Service
public class TestCaseService {

    /** Logger */
    private static Logger log = LoggerFactory.getLogger(TestCaseService.class);

    @Autowired
    private List<TestActionConverter<?, ? extends com.consol.citrus.TestAction>> actionConverter;

    /**
     * Lists all available Citrus test cases grouped in test packages.
     * @param project
     * @return
     */
    public List<TestGroup> getTestPackages(Project project) {
        Map<String, TestGroup> testPackages = new LinkedHashMap<>();

        List<File> sourceFiles = FileUtils.findFiles(project.getJavaDirectory(), StringUtils.commaDelimitedListToSet(project.getSettings().getJavaFilePattern()));
        List<Test> tests = findTests(project, sourceFiles);

        for (Test test : tests) {
            if (!testPackages.containsKey(test.getPackageName())) {
                TestGroup testPackage = new TestGroup();
                testPackage.setName(test.getPackageName());
                testPackages.put(test.getPackageName(), testPackage);
            }

            testPackages.get(test.getPackageName()).getTests().add(test);
        }

        return Arrays.asList(testPackages.values().toArray(new TestGroup[testPackages.size()]));
    }

    /**
     * List test names of latest, meaning newest or last modified tests in project.
     *
     * @param project
     * @return
     */
    public List<TestGroup> getLatest(Project project, int limit) {
        Map<String, TestGroup> grouped = new LinkedHashMap<>();

        List<File> sourceFiles = FileUtils.findFiles(project.getJavaDirectory(), StringUtils.commaDelimitedListToSet(project.getSettings().getJavaFilePattern()));
        sourceFiles = sourceFiles.stream()
                .sorted((f1, f2) -> f1.lastModified() >= f2.lastModified() ? -1 : 1)
                .limit(limit)
                .collect(Collectors.toList());

        List<Test> tests = findTests(project, sourceFiles);
        for (Test test : tests) {
            if (!grouped.containsKey(test.getClassName())) {
                TestGroup testGroup = new TestGroup();
                testGroup.setName(test.getClassName());
                grouped.put(test.getClassName(), testGroup);
            }

            grouped.get(test.getClassName()).getTests().add(test);
        }

        return Arrays.asList(grouped.values().toArray(new TestGroup[grouped.size()]));
    }

    /**
     * Finds test for given package, class and method name.
     * @param project
     * @param packageName
     * @param className
     * @param methodName
     * @return
     */
    public Test findTest(Project project, String packageName, String className, String methodName) {
        FileSystemResource sourceFile = new FileSystemResource(project.getJavaDirectory() + packageName.replace('.', File.separatorChar) + System.getProperty("file.separator") + className + ".java");
        if (!sourceFile.exists()) {
            throw new ApplicationRuntimeException("Unable to find test source file: " + className + " in " + project.getJavaDirectory());
        }

        Optional<Test> test = findTests(sourceFile.getFile(), packageName, className)
                .stream()
                .filter(candidate -> methodName.equals(candidate.getMethodName())).findFirst();

        if (test.isPresent()) {
            return test.get();
        } else {
            throw new ApplicationRuntimeException("Unable to find test: " + className + "." + methodName);
        }

    }

    /**
     * Finds all tests case declarations in given source files. Method is loading tests by their annotation presence of @CitrusTest or @CitrusXmlTest.
     * @param project
     * @param sourceFiles
     */
    private List<Test> findTests(Project project, List<File> sourceFiles) {
        List<Test> tests = new ArrayList<>();
        for (File sourceFile : sourceFiles) {
            String className = FilenameUtils.getBaseName(sourceFile.getName());
            String testPackageName = sourceFile.getPath().substring(project.getJavaDirectory().length(), sourceFile.getPath().length() - sourceFile.getName().length())
                    .replace(File.separatorChar, '.');

            if (testPackageName.endsWith(".")) {
                testPackageName = testPackageName.substring(0, testPackageName.length() - 1);
            }

            tests.addAll(findTests(sourceFile, testPackageName, className));
        }

        return tests;
    }

    /**
     * Find all tests in give source file. Method is finding tests by their annotation presence of @CitrusTest or @CitrusXmlTest.
     * @param sourceFile
     * @param packageName
     * @param className
     * @return
     */
    private List<Test> findTests(File sourceFile, String packageName, String className) {
        List<Test> tests = new ArrayList<>();

        try {
            String sourceCode = FileUtils.readToString(new FileSystemResource(sourceFile));

            Matcher matcher = Pattern.compile("[^/\\*]\\s@CitrusTest").matcher(sourceCode);
            while (matcher.find()) {
                Test test = new Test();
                test.setType(TestType.JAVA);
                test.setClassName(className);
                test.setPackageName(packageName);

                String snippet = StringUtils.trimAllWhitespace(sourceCode.substring(matcher.start()));
                snippet = snippet.substring(0, snippet.indexOf("){"));
                String methodName = snippet.substring(snippet.indexOf("publicvoid") + 10);
                methodName = methodName.substring(0, methodName.indexOf("("));
                test.setMethodName(methodName);

                if (snippet.contains("@CitrusTest(name=")) {
                    String explicitName = snippet.substring(snippet.indexOf("name=\"") + 6);
                    explicitName = explicitName.substring(0, explicitName.indexOf("\""));
                    test.setName(explicitName);
                } else {
                    test.setName(className + "." + methodName);
                }

                tests.add(test);
            }

            matcher = Pattern.compile("[^/\\*]\\s@CitrusXmlTest").matcher(sourceCode);
            while (matcher.find()) {
                Test test = new Test();
                test.setType(TestType.XML);
                test.setClassName(className);
                test.setPackageName(packageName);

                String snippet = StringUtils.trimAllWhitespace(sourceCode.substring(matcher.start()));
                snippet = snippet.substring(0, snippet.indexOf('{', snippet.indexOf("publicvoid")));
                String methodName = snippet.substring(snippet.indexOf("publicvoid") + 10);
                methodName = methodName.substring(0, methodName.indexOf("("));
                test.setMethodName(methodName);

                if (snippet.contains("@CitrusXmlTest(name=\"")) {
                    String explicitName = snippet.substring(snippet.indexOf("name=\"") + 6);
                    explicitName = explicitName.substring(0, explicitName.indexOf("\""));
                    test.setName(explicitName);
                } else if (snippet.contains("@CitrusXmlTest(name={\"")) {
                    String explicitName = snippet.substring(snippet.indexOf("name={\"") + 7);
                    explicitName = explicitName.substring(0, explicitName.indexOf("\""));
                    test.setName(explicitName);
                } else {
                    test.setName(methodName);
                }

                if (snippet.contains("packageScan=\"")) {
                    String packageScan = snippet.substring(snippet.indexOf("packageScan=\"") + 13);
                    packageScan = packageScan.substring(0, packageScan.indexOf("\""));
                    test.setPackageName(packageScan);
                }

                if (snippet.contains("packageName=\"")) {
                    String explicitPackageName = snippet.substring(snippet.indexOf("packageName=\"") + 13);
                    explicitPackageName = explicitPackageName.substring(0, explicitPackageName.indexOf("\""));
                    test.setPackageName(explicitPackageName);
                }

                tests.add(test);
            }
        } catch (IOException e) {
            log.error("Failed to read test source file", e);
        }

        return tests;
    }

    /**
     * Gets test case details such as status, description, author.
     * @param project
     * @param test
     * @return
     */
    public TestDetail getTestDetail(Project project, Test test) {
        TestDetail testDetail = new TestDetail(test);
        TestcaseModel testModel = getTestModel(project, testDetail);

        if (testModel.getVariables() != null) {
            for (VariablesModel.Variable variable : testModel.getVariables().getVariables()) {
                testDetail.getVariables().put(variable.getName(), variable.getValue());
            }
        }

        if (testModel.getDescription() != null) {
            testDetail.setDescription(testModel.getDescription().trim().replaceAll(" +", " ").replaceAll("\t", ""));
        }

        if (testModel.getMetaInfo() != null) {
            testDetail.setAuthor(testModel.getMetaInfo().getAuthor());
            if (testModel.getMetaInfo().getLastUpdatedOn() != null) {
                testDetail.setLastModified(testModel.getMetaInfo().getLastUpdatedOn().getTimeInMillis());
            }
        }

        if (test.getType().equals(TestType.JAVA)) {
            testDetail.setFile(project.getJavaDirectory() + test.getPackageName().replace('.', File.separatorChar) + File.separator + test.getClassName());
        } else {
            testDetail.setFile(project.getXmlDirectory() + test.getPackageName().replace('.', File.separatorChar) + File.separator + FilenameUtils.getBaseName(test.getName()));
        }

        if (testModel.getActions() != null) {
            for (Object actionType : testModel.getActions().getActionsAndSendsAndReceives()) {
                TestAction model = null;
                for (TestActionConverter converter : actionConverter) {
                    if (converter.getSourceModelClass().isInstance(actionType)) {
                        model = converter.convert(actionType);
                        break;
                    }
                }

                if (model == null) {
                    if (actionType.getClass().getAnnotation(XmlRootElement.class) == null) {
                        log.info(actionType.getClass().getName());
                    } else {
                        model = new ActionConverter(actionType.getClass().getAnnotation(XmlRootElement.class).name()).convert(actionType);
                    }
                }

                if (model != null) {
                    testDetail.getActions().add(model);
                }
            }
        }

        return testDetail;
    }

    /**
     * Gets the source code for the given test.
     * @param relativePath
     * @return
     */
    public String getSourceCode(Project project, String relativePath) {
        try {
            String sourcePath = project.getAbsolutePath(relativePath);
            if (new File(sourcePath).exists()) {
                return FileUtils.readToString(new FileInputStream(sourcePath));
            } else {
                throw new ApplicationRuntimeException("Unable to find source code for path: " + sourcePath);
            }
        } catch (IOException e) {
            throw new ApplicationRuntimeException("Failed to load test case source code", e);
        }
    }

    /**
     * Updates the source code for the given test.
     * @param relativePath
     * @param sourceCode
     * @return
     */
    public void updateSourceCode(Project project, String relativePath, String sourceCode) {
        String sourcePath = project.getAbsolutePath(relativePath);
        FileUtils.writeToFile(sourceCode, new File(sourcePath));
    }

    /**
     * Get total number of tests in project.
     * @param project
     * @return
     */
    public long getTestCount(Project project) {
        long testCount = 0L;
        try {
            List<File> sourceFiles = FileUtils.findFiles(project.getJavaDirectory(), StringUtils.commaDelimitedListToSet(project.getSettings().getJavaFilePattern()));
            for (File sourceFile : sourceFiles) {
                String sourceCode = FileUtils.readToString(new FileSystemResource(sourceFile));

                testCount += StringUtils.countOccurrencesOf(sourceCode, "@CitrusTest");
                testCount += StringUtils.countOccurrencesOf(sourceCode, "@CitrusXmlTest");
            }
        } catch (IOException e) {
            log.warn("Failed to read Java source files - list of test cases for this project is incomplete", e);
        }

        return testCount;
    }

    /**
     * Reads either XML or Java test definition to model class.
     * @param project
     * @return
     */
    private TestcaseModel getTestModel(Project project, TestDetail detail) {
        if (detail.getType().equals(TestType.XML)) {
            return getXmlTestModel(project, detail);
        } else if (detail.getType().equals(TestType.JAVA)) {
            return getJavaTestModel(project, detail);
        } else {
            throw new ApplicationRuntimeException("Unsupported test case type: " + detail.getType());
        }
    }

    /**
     * Get test case model from XML source code.
     * @param test
     * @return
     */
    private TestcaseModel getXmlTestModel(Project project, Test test) {
        String xmlSource = getSourceCode(project, test.getRelativePath());

        if (!StringUtils.hasText(xmlSource)) {
            throw new ApplicationRuntimeException("Failed to get XML source code for test: " + test.getPackageName() + "." + test.getName());
        }

        return ((SpringBeans) new XmlTestMarshaller().unmarshal(new StringSource(xmlSource))).getTestcase();
    }

    /**
     * Get test case model from Java source code.
     * @param project
     * @param detail
     * @return
     */
    private TestcaseModel getJavaTestModel(Project project, TestDetail detail) {
        if (project.isMavenProject()) {
            try {
                FileUtils.setSimulationMode(true);
                ClassLoader classLoader = project.getClassLoader();
                Class testClass = classLoader.loadClass(detail.getPackageName() + "." + detail.getClassName());

                if (TestSimulator.class.isAssignableFrom(testClass)) {
                    TestSimulator testInstance = (TestSimulator) testClass.newInstance();
                    Method testMethod = ReflectionUtils.findMethod(testClass, detail.getMethodName());

                    Mocks.injectMocks(testInstance);

                    testInstance.simulate(testMethod, Mocks.getTestContextMock(), Mocks.getApplicationContextMock());
                    testMethod.invoke(testInstance);

                    return getTestcaseModel(testInstance.getTestCase());
                }
            } catch (MalformedURLException e) {
                throw new ApplicationRuntimeException("Failed to access Java classes output folder", e);
            } catch (ClassNotFoundException | NoClassDefFoundError | NoResolvedResultException e) {
                log.error("Failed to load Java test class", e);
            } catch (IOException e) {
                throw new ApplicationRuntimeException("Failed to access project output folder", e);
            } catch (InstantiationException | IllegalAccessException e) {
                log.error("Failed to create test class instance", e);
            } catch (InvocationTargetException e) {
                log.error("Failed to invoke test method", e);
            } finally {
                FileUtils.setSimulationMode(false);
            }
        }

        TestcaseModel testModel = new TestcaseModel();
        testModel.setName(detail.getClassName() + "." + detail.getMethodName());
        return testModel;
    }

    private TestcaseModel getTestcaseModel(TestCase testCase) {
        TestcaseModel testModel = new TestcaseModel();
        testModel.setName(testCase.getName());

        VariablesModel variablesModel = new VariablesModel();
        for (Map.Entry<String, Object> entry : testCase.getVariableDefinitions().entrySet()) {
            VariablesModel.Variable variable = new VariablesModel.Variable();
            variable.setName(entry.getKey());
            variable.setValue(entry.getValue().toString());
            variablesModel.getVariables().add(variable);
        }
        testModel.setVariables(variablesModel);

        TestActionsType actions = new TestActionsType();
        for (com.consol.citrus.TestAction action : testCase.getActions()) {
            actions.getActionsAndSendsAndReceives().add(getActionModel(action));
        }

        testModel.setActions(actions);
        return testModel;
    }

    private Object getActionModel(com.consol.citrus.TestAction action) {
        com.consol.citrus.TestAction model;

        if (action instanceof DelegatingTestAction) {
            model = ((DelegatingTestAction) action).getDelegate();
        } else {
            model = action;
        }

        for (TestActionConverter converter : actionConverter) {
            if (converter.getActionModelClass().isInstance(model)) {
                return converter.convertModel(model);
            }
        }

        return new ActionConverter(action.getName()).convertModel(model);
    }

}
