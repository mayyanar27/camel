/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms.tx;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.processor.DefaultErrorHandler;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.spring.spi.TransactionErrorHandler;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

/**
 * Test case derived from:
 * http://camel.apache.org/transactional-client.html and Martin
 * Krasser's sample:
 * http://www.nabble.com/JMS-Transactions---How-To-td15168958s22882.html#a15198803
 *
 * @author Kevin Ross
 */
public abstract class AbstractTransactionTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        setCamelContextService(null);
        context = null;
        template = null;
    }

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/component/jms/tx/JavaDSLTransactionTest.xml");
    }

    protected void assertResult() throws InterruptedException {
        template.sendBody("activemq:queue:foo", "blah");
        Thread.sleep(3000L);        
        assertTrue("Expected only 2 calls to process() (1 failure, 1 success) but encountered "
                   + getConditionalExceptionProcessor().getCount() + "."
                   , getConditionalExceptionProcessor().getCount() == 2);
    }

    protected ConditionalExceptionProcessor getConditionalExceptionProcessor() {

        Route route = context.getRoutes().get(0);
        assertNotNull(route);
        return getConditionalExceptionProcessor(route);
    }

    /**
     * By default routes should be wrapped in the {@link DeadLetterChannel} so
     * lets unwrap that and return the actual processor
     */
    protected ConditionalExceptionProcessor getConditionalExceptionProcessor(Route route) {

        //
        // the following is very specific (and brittle) and is not generally
        // useful outside these transaction tests (nor intended to be).
        //
        EventDrivenConsumerRoute consumerRoute = assertIsInstanceOf(EventDrivenConsumerRoute.class, route);
        Processor processor = findProcessorByClass(consumerRoute.getProcessor(),
                                                   ConditionalExceptionProcessor.class);
        return assertIsInstanceOf(ConditionalExceptionProcessor.class, processor);
    }

    /**
     * Find the first instance of a Processor of a given class.
     */
    protected Processor findProcessorByClass(Processor processor, Class findClass) {
        while (true) {
            processor = unwrapDeadLetter(processor);

            if (processor instanceof DelegateAsyncProcessor) {
                processor = ((DelegateAsyncProcessor)processor).getProcessor();
            } else if (processor instanceof DelegateProcessor) {
                // TransactionInterceptor is a DelegateProcessor
                processor = ((DelegateProcessor)processor).getProcessor();
            } else if (processor instanceof Pipeline) {
                for (Processor p : ((Pipeline)processor).getProcessors()) {
                    p = findProcessorByClass(p, findClass);
                    if (p != null && p.getClass().isAssignableFrom(findClass)) {
                        processor = p;
                        return processor;
                    }
                }
            } else {
                return processor;
            }
        }
    }

    private Processor unwrapDeadLetter(Processor processor) {
        if (processor instanceof DeadLetterChannel) {
            processor = ((DeadLetterChannel)processor).getOutput();
        }
        if (processor instanceof DefaultErrorHandler) {
            processor = ((DefaultErrorHandler)processor).getOutput();
        }
        if (processor instanceof TransactionErrorHandler) {
            processor = ((TransactionErrorHandler)processor).getOutput();
        }
        return processor;
    }
}
