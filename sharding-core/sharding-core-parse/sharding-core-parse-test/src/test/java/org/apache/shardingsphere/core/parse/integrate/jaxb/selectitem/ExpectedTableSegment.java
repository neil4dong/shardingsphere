/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.shardingsphere.core.parse.integrate.jaxb.selectitem;

import lombok.Getter;
import lombok.Setter;
import org.apache.shardingsphere.core.parse.core.constant.QuoteCharacter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpectedTableSegment {

    @XmlAttribute(name="start-index")
    protected Integer startIndex;

    @XmlAttribute(name = "stop-index")
    private Integer stopIndex;

    @XmlAttribute
    private String name;

    @XmlAttribute
    private QuoteCharacter quoteCharacter = QuoteCharacter.NONE;

    @XmlElement
    private String owner;

    @XmlAttribute
    private String alias;
}
