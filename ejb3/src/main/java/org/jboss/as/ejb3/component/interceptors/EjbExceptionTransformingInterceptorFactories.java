/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.component.interceptors;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;

import jakarta.ejb.CreateException;
import jakarta.ejb.EJBException;
import jakarta.ejb.EJBTransactionRequiredException;
import jakarta.ejb.EJBTransactionRolledbackException;
import jakarta.ejb.NoSuchEJBException;
import jakarta.ejb.NoSuchEntityException;
import jakarta.ejb.NoSuchObjectLocalException;
import jakarta.ejb.TransactionRequiredLocalException;
import jakarta.ejb.TransactionRolledbackLocalException;
import jakarta.transaction.TransactionRequiredException;
import jakarta.transaction.TransactionRolledbackException;

import org.jboss.as.ejb3.component.EJBComponentUnavailableException;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.invocation.Interceptor;
import org.jboss.invocation.InterceptorContext;
import org.jboss.invocation.InterceptorFactory;

/**
 * An interceptor that transforms Enterprise Beans 3.0 business interface exceptions to Enterprise Beans 2.x exceptions when required.
 * <p/>
 * This allows us to keep the actual
 *
 * @author Stuart Douglas
 */
public class EjbExceptionTransformingInterceptorFactories {

    /**
     * We need to return a CreateException to the client.
     * <p/>
     * Rather than forcing all create exceptions everywhere to propagate, and generally making a mess, we stash
     * the exception here, and then re-throw it from the exception transforming interceptor.
     */
    private static final ThreadLocal<CreateException> CREATE_EXCEPTION = new ThreadLocal<CreateException>();

    private static <T extends Throwable> T copyCause(T newThrowable, Throwable originalThrowable) {
        Throwable cause = originalThrowable.getCause();
        if (cause != null) try {
            newThrowable.initCause(cause);
        } catch (IllegalStateException ignored) {
            // some exceptions rudely don't allow cause initialization
        }
        return newThrowable;
    }

    private static <T extends Throwable> T copyStackTrace(T newThrowable, Throwable originalThrowable) {
        newThrowable.setStackTrace(originalThrowable.getStackTrace());
        return newThrowable;
    }

    public static final InterceptorFactory REMOTE_INSTANCE = new ImmediateInterceptorFactory(new Interceptor() {
        @Override
        public Object processInvocation(final InterceptorContext context) throws Exception {
            try {
                return context.proceed();
            } catch (EJBTransactionRequiredException e) {
                // this exception explicitly forbids initializing a cause
                throw copyStackTrace(new TransactionRequiredException(e.getMessage()), e);
            } catch (EJBTransactionRolledbackException e) {
                // this exception explicitly forbids initializing a cause
                throw copyStackTrace(new TransactionRolledbackException(e.getMessage()), e);
            } catch (NoSuchEJBException e) {
                // this exception explicitly forbids initializing a cause
                throw copyStackTrace(new NoSuchObjectException(e.getMessage()), e);
            } catch (NoSuchEntityException e) {
                // this exception explicitly forbids initializing a cause
                throw copyStackTrace(new NoSuchObjectException(e.getMessage()), e);
            } catch(EJBComponentUnavailableException e) {
                // do not wrap this exception in RemoteException as it is not destined for the client (WFLY-13871)
                throw e;
            } catch (EJBException e) {
                //as the create exception is not propagated the init method interceptor just stashes it in a ThreadLocal
                CreateException createException = popCreateException();
                if (createException != null) {
                    throw createException;
                }
                throw new RemoteException("Invocation failed", e);
            }
        }
    });

    public static final InterceptorFactory LOCAL_INSTANCE = new ImmediateInterceptorFactory(new Interceptor() {
        @Override
        public Object processInvocation(final InterceptorContext context) throws Exception {
            try {
                return context.proceed();
            } catch (EJBTransactionRequiredException e) {
                throw copyStackTrace(copyCause(new TransactionRequiredLocalException(e.getMessage()), e), e);
            } catch (EJBTransactionRolledbackException e) {
                throw copyStackTrace(new TransactionRolledbackLocalException(e.getMessage(), e), e);
            } catch (NoSuchEJBException e) {
                throw copyStackTrace(new NoSuchObjectLocalException(e.getMessage(), e), e);
            } catch (NoSuchEntityException e) {
                throw copyStackTrace(new NoSuchObjectLocalException(e.getMessage(), e), e);
            } catch (EJBException e) {
                CreateException createException = popCreateException();
                if (createException != null) {
                    throw createException;
                }
                throw e;
            }
        }
    });

    public static void setCreateException(CreateException exception) {
        CREATE_EXCEPTION.set(exception);
    }

    public static CreateException popCreateException() {
        try {
            return CREATE_EXCEPTION.get();
        } finally {
            CREATE_EXCEPTION.remove();
        }
    }

}
