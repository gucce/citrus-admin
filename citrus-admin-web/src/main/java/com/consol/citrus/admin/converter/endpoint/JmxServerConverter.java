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

package com.consol.citrus.admin.converter.endpoint;

import com.consol.citrus.admin.model.EndpointModel;
import com.consol.citrus.message.MessageConverter;
import com.consol.citrus.model.config.jmx.JmxServerModel;
import org.springframework.stereotype.Component;

/**
 * @author Christoph Deppisch
 */
@Component
public class JmxServerConverter extends AbstractEndpointConverter<JmxServerModel> {

    public static final String TRUE = "true";
    public static final String FALSE = "false";

    @Override
    public EndpointModel convert(JmxServerModel model) {
        EndpointModel endpointModel = new EndpointModel(getEndpointType(), model.getId(), getSourceModelClass().getName());

        endpointModel.add(property("serverUrl", model));
        endpointModel.add(property("host", model));
        endpointModel.add(property("port", model));
        endpointModel.add(property("protocol", model));
        endpointModel.add(property("binding", model));
        endpointModel.add(property("environmentProperties", model));
        endpointModel.add(property("createRegistry", model, TRUE)
                .options(TRUE, FALSE));
        endpointModel.add(property("autoStart", model, TRUE)
                .options(TRUE, FALSE));
        endpointModel.add(property("messageConverter", model)
                .optionKey(MessageConverter.class.getName()));

        addEndpointProperties(endpointModel, model);

        return endpointModel;
    }

    @Override
    public Class<JmxServerModel> getSourceModelClass() {
        return JmxServerModel.class;
    }

    @Override
    public String getEndpointType() {
        return "jmx-server";
    }
}
