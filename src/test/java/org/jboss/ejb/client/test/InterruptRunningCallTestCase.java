package org.jboss.ejb.client.test;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.naming.NamingException;

import org.jboss.ejb.client.test.common.Echo;
import org.jboss.ejb.client.test.common.EchoBean;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;


public class InterruptRunningCallTestCase extends AbstractEJBClientTestCase {
    private static final Logger logger = Logger.getLogger(InterruptRunningCallTestCase.class);

    @Before
    public void beforeTest() throws Exception {
        // start a server
        startServer(0);
        // deploy a custom bean
        String longResponse = generateLongResponse(131072 * 10);
        deployCustomBean(0, APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getSimpleName(), new EchoBean(longResponse));
    }

    @After
    public void afterTest() {
        undeployCustomBean(0, APP_NAME, MODULE_NAME, DISTINCT_NAME, EchoBean.class.getName());
        stopServer(0);
    }

    @Test
    public void testInterruptingLongRunningRequests() throws Exception
    {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        props.put("java.naming.provider.url", "remote://localhost:6999");

        WildFlyRootContext context = new WildFlyRootContext(props);

        ExecutorService executorService = Executors.newFixedThreadPool(1);
        Future<?> future = null;

        for (int i = 0; i < 10; i++)
        {
            if (future != null) {
                future.cancel(true);
            }

            CountDownLatch latch = new CountDownLatch(1);
            future = executorService.submit(echoCallable(context, latch));

            latch.await(5, TimeUnit.SECONDS);
            Thread.sleep(10);
        }

        future.get();

        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        long stackedThreads = countThreadsStackedInOutboundMessageAccept(stackTraces);
        Assert.assertEquals("Threads are stacked in OutboundMessage$1.accept", 0, stackedThreads);
    }

    private long countThreadsStackedInOutboundMessageAccept(Map<Thread, StackTraceElement[]> stackTraces) {
        return stackTraces.entrySet().stream()
                .filter(e -> e.getKey().getName().startsWith("Remoting \"test-server\" task-"))
                .filter(e -> Arrays.stream(e.getValue())
                        .anyMatch(s -> s.getClassName().contains("OutboundMessage") && s.getMethodName().contains("accept")))
                .peek(e -> logger.info(e.getKey() + " stacked at:\n" + Arrays.stream(e.getValue()).map(String::valueOf).collect(Collectors.joining("\n\t"))))
                .count();
    }

    private Callable<String> echoCallable(WildFlyRootContext context, CountDownLatch latch) {
        return () -> {
            Echo echo = (Echo) lookupBean(context);
            if (echo != null) {

                try {
                    latch.countDown();
                    Thread.yield();
                    echo.echo("echoCallable");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else
            {
                logger.error("Failed to lookup the remote bean. Invalid test setup");
            }
            return "done";
        };
    }

    private Object lookupBean(WildFlyRootContext context) {
        try {
            return context.lookup("ejb:" + APP_NAME + "/" + MODULE_NAME + "/" + EchoBean.class.getSimpleName() + "!" + Echo.class.getName() + "?stateful");
        } catch (NamingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String generateLongResponse(int size) {
        StringBuilder stringBuilder = new StringBuilder("generated long test: ");
        String str = new Random().ints(size, 32, 125)
                .mapToObj(i -> String.valueOf((char) i))
                .collect(Collectors.joining());
        stringBuilder.append(str);
        return stringBuilder.toString();
    }
}
