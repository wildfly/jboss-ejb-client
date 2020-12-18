/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package org.jboss.ejb.client.test.common;

import org.jboss.logging.Logger;

/**
 * User: jpai
 */
public class EchoBean implements Echo {

    private static final Logger logger = Logger.getLogger(EchoBean.class);
    private final String node;

    public EchoBean() {
        this("unknown");
    }

    public EchoBean(String node) {
        this.node = node;
    }

    @Override
    public Result<String> echo(String msg) {
        logger.info(this.getClass().getSimpleName() + " echoing message " + msg);
        if ("request to throw IllegalArgumentException".equals(msg)) {
            throw new IllegalArgumentException("Intentionally thrown upon request from caller");
        }
        return new Result<String>(msg, node);
    }

    @Override
    public Result<String> echoNonTx(String msg) {
        logger.info(this.getClass().getSimpleName() + " echoing message (NonTx) " + msg);
        return new Result<String>(msg, node);
    }

}
