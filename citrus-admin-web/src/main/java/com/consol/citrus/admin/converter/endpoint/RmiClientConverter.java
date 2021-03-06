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
import com.consol.citrus.message.MessageCorrelator;
import com.consol.citrus.model.config.rmi.RmiClientModel;
import org.springframework.stereotype.Component;

/**
 * @author Christoph Deppisch
 */
@Component
public class RmiClientConverter extends AbstractEndpointConverter<RmiClientModel> {

    @Override
    public EndpointModel convert(RmiClientModel model) {
        EndpointModel endpointModel = new EndpointModel(getEndpointType(), model.getId(), getSourceModelClass().getName());

        endpointModel.add(property("serverUrl", model));
        endpointModel.add(property("host", model));
        endpointModel.add(property("port", model));
        endpointModel.add(property("binding", model));
        endpointModel.add(property("method", model));
        endpointModel.add(property("messageCorrelator", model)
                .optionKey(MessageCorrelator.class.getName()));
        endpointModel.add(property("messageConverter", model)
                .optionKey(MessageConverter.class.getName()));
        endpointModel.add(property("pollingInterval", model, "500"));

        addEndpointProperties(endpointModel, model);

        return endpointModel;
    }

    @Override
    public Class<RmiClientModel> getSourceModelClass() {
        return RmiClientModel.class;
    }

    @Override
    public String getEndpointType() {
        return "rmi-client";
    }
}
