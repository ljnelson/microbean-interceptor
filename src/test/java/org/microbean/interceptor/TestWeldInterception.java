/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2023–2024 microBean™.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.microbean.interceptor;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.annotation.PostConstruct;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

import jakarta.enterprise.inject.spi.BeanManager;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

final class TestWeldInterception {

  private SeContainer container;

  private TestWeldInterception() {
    super();
  }

  @BeforeEach
  final void startContainer() {
    ExternalInterceptor.count = 0;
    Bean.count = 0;
    this.container = SeContainerInitializer.newInstance()
      .disableDiscovery()
      .addBeanClasses(Bean.class, ExternalInterceptor.class)
      .enableInterceptors(ExternalInterceptor.class)
      .initialize();

  }

  @AfterEach
  final void stopContainer() {
    if (this.container != null) {
      this.container.close();
    }
  }

  @Test
  final void testInterception() {
    // See also https://issues.redhat.com/browse/CDI-287.
    // See also https://issues.redhat.com/browse/CDI-339.
    // ExternalInterceptor.aroundConstructMethod()
    //   Bean()
    //   Bean.initializerMethod()
    // Bean.postConstructMethod()
    final Bean b = this.container.select(Bean.class).get();
    System.out.println("Bean selected");
    // ExternalInterceptor.aroundInvokeMethod()
    //   Bean.aroundInvokeMethod()
    //     Bean.businessMethod()
    b.businessMethod();
    assertEquals(1, ExternalInterceptor.count);
    assertEquals(2, Bean.count);
    final BeanManager bm = this.container.select(BeanManager.class).get();
    // Create a contextual *instance* and note that a new interceptor is created
    bm.resolve(bm.getBeans(Bean.class)).create(bm.createCreationalContext(null));
    
  }

  @Interceptor
  // See also https://issues.redhat.com/browse/WELD-1416
  // See also https://issues.redhat.com/browse/WELD-1945
  @Verbose
  private static class ExternalInterceptor {

    private static int count;

    ExternalInterceptor() {
      super();
      count++;
      System.out.println("ExternalInterceptor.ExternalInterceptor()");
    }

    @AroundConstruct
    void aroundConstructMethod(final InvocationContext ic) throws Exception {
      System.out.println("ExternalInterceptor.aroundConstructMethod()");
      assertNull(ic.getTarget());
      ic.proceed();
      final Object target0 = ic.getTarget();
      assertNotNull(target0);
      ic.proceed();
      final Object target1 = ic.getTarget();
      assertNotNull(target1);
      assertNotSame(target0, target1);
    }

    @AroundConstruct
    Object anotherAroundConstructMethod(final InvocationContext ic) throws Exception {
      System.out.println("ExternalInterceptor.anotherAroundConstructMethod()");
      ic.proceed();
      return Integer.valueOf(47);
    }

    @PostConstruct
    void postConstructMethod(final InvocationContext ic) throws Exception {
      System.out.println("ExternalInterceptor.postConstructMethod()");
      ic.proceed(); // or not
    }

    @AroundInvoke
    Object aroundInvokeMethod(final InvocationContext ic) throws Exception {
      System.out.println("ExternalInterceptor.aroundInvokeMethod()");
      Object rv = ic.proceed();
      return rv;
    }

  }

  @Singleton
  @Verbose
  private static class Bean {

    private static int count;

    @Inject
    Bean() {
      super();
      System.out.println("Bean()");
      count++;
    }

    @Inject
    void initializerMethod() {
      System.out.println("Bean.initializerMethod()");
    }

    // "Lifecycle callback interceptor methods declared in a target class…must have the following signature:
    //   void <METHOD>()"
    @PostConstruct
    void postConstructMethod() {
      System.out.println("Bean.postConstructMethod()");
    }

    void businessMethod() {
      System.out.println("Bean.businessMethod()");
    }

    @AroundInvoke
    Object aroundInvokeMethod(final InvocationContext ic) throws Exception {
      System.out.println("Bean.aroundInvokeMethod()");
      return ic.proceed();
    }

  }

  @InterceptorBinding
  @Retention(RUNTIME)
  @Target({ CONSTRUCTOR, METHOD, TYPE})
  private @interface Verbose {

  }

}
