/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.myfaces.buildtools.maven2.plugin.builder.annotation;

public @interface JSFBehavior
{
    /**
     * Indicate the behaviorId which identifies this class. If not defined, it
     * tries to get the value of the field BEHAVIOR_ID.
     */
    String id() default "";
    
    /**
     * The name of the component in a page (ex: x:mycomp).
     */
    String name() default "";
    
    /**
     * Indicate if the element accept inner elements or not.
     */
    String bodyContent() default "";

    /**
     * Short description
     */
    String desc() default "";
}