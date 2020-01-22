/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.test.scripting.secure;


import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Joram Barrez
 */
public class SecureScriptingTest extends SecureScriptingBaseTest {

    @Test
    public void testClassWhiteListing() {
        deployProcessDefinition("test-secure-script-class-white-listing.bpmn20.xml");

        try {
            runtimeService.startProcessInstanceByKey("secureScripting");
            Assert.fail(); // Expecting exception
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(e.getMessage().contains("Cannot call property getRuntime in object"));
        }
    }

    @Test
    public void testInfiniteLoop() {
        deployProcessDefinition("test-secure-script-infinite-loop.bpmn20.xml");

        enableSysoutsInScript();
        addWhiteListedClass("java.lang.Thread"); // For the thread.sleep

        Throwable t = catchThrowable(() -> runtimeService.startProcessInstanceByKey("secureScripting"));
        Assert.assertTrue(t.getMessage().contains("Maximum variableScope time of 3000 ms exceeded"));
    }

    @Test
    public void testMaximumStackDepth() {
        deployProcessDefinition("test-secure-script-max-stack-depth.bpmn20.xml");

        Throwable t = catchThrowable(() -> runtimeService.startProcessInstanceByKey("secureScripting"));
        Assert.assertTrue(t.getMessage().contains("Exceeded maximum stack depth"));
    }

    @Test
    public void testMaxMemoryUsage() {
        deployProcessDefinition("test-secure-script-max-memory-usage.bpmn20.xml");

        Throwable t = catchThrowable(() -> runtimeService.startProcessInstanceByKey("secureScripting"));
        Assert.assertTrue(t.getMessage().contains("Memory limit of 3145728 bytes reached"));
    }

    @Test
    public void testUseExecutionAndVariables() {
        deployProcessDefinition("test-secure-script-use-variableScope-and-vars.bpmn20.xml");

        addWhiteListedClass("java.lang.Integer");
        addWhiteListedClass("org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl");

        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 123);
        vars.put("b", 456);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("useExecutionAndVars", vars);

        Object c = runtimeService.getVariable(processInstance.getId(), "c");
        Assert.assertTrue(c instanceof Number);
        Number cNumber = (Number) c;
        Assert.assertEquals(579, cNumber.intValue());

        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();
        Assert.assertEquals(1, tasks.size());
    }

    @Test
    public void testExecutionListener() {
        deployProcessDefinition("test-secure-script-execution-listener.bpmn20.xml");
        try {
            runtimeService.startProcessInstanceByKey("secureScripting");
            Assert.fail(); // Expecting exception
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(e.getMessage().contains("Cannot call property getRuntime in object"));
        }
        Assert.assertEquals(0, taskService.createTaskQuery().count());
    }

    @Test
    public void testExecutionListener2() throws Exception {
        deployProcessDefinition("test-secure-script-execution-listener2.bpmn20.xml");

        removeWhiteListedClass("org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl");
        try {
            runtimeService.startProcessInstanceByKey("secureScripting");
            Assert.fail(); // Expecting exception
        } catch (Exception e) {

        }

        try {
            addWhiteListedClass("org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl");
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("secureScripting");
            Assert.assertEquals("testValue", runtimeService.getVariable(processInstance.getId(), "test"));
        } finally {
            removeWhiteListedClass("org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl");
        }
    }

    @Test
    public void testTaskListener() {
        deployProcessDefinition("test-secure-script-task-listener.bpmn20.xml");
        runtimeService.startProcessInstanceByKey("secureScripting");

        Task task = taskService.createTaskQuery().singleResult();
        Assert.assertNotNull(task);

        // Completing the task should fail cause the script is not secure
        try {
            taskService.complete(task.getId());
            Assert.fail(); // Expecting exception
        } catch (Exception e) {
            e.printStackTrace();
        }

        task = taskService.createTaskQuery().singleResult();
        Assert.assertNotNull(task);
    }

    @Test
    public void testAccessToBeans() {
        deployProcessDefinition("test-secure-script-spring-bean-access.bpmn20.xml");
        addWhiteListedClass(ExposedBean.class.getName());
        addWhiteListedClass(ExecutionEntityImpl.class.getCanonicalName());
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("useSpringBeans");
        Object c = runtimeService.getVariable(processInstance.getId(), "c");
        Assert.assertTrue(c instanceof Number);
        Number cNumber = (Number) c;
        Assert.assertEquals(exposedBean.getNumber(), cNumber.intValue());
    }
}
