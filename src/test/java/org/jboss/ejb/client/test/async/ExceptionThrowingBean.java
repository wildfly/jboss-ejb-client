package org.jboss.ejb.client.test.async;

/**
 * @author: Jaikiran Pai
 */
public class ExceptionThrowingBean implements ExceptionThrower {


    @Override
    public void justThrowBackSomeException() {
        throw new RuntimeException("Intentionally thrown exception from " + this.getClass().getName());
    }
}
