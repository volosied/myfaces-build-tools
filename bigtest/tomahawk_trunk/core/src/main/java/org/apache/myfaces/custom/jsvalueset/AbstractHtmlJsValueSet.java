/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.custom.jsvalueset;

import org.apache.myfaces.component.UserRoleUtils;
import org.apache.myfaces.shared_tomahawk.util._ComponentUtils;

import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;

/**
 * @JSFComponent
 *   name = "t:jsValueSet"
 *   class = "org.apache.myfaces.custom.jsvalueset.HtmlJsValueSet"
 *   superClass = "org.apache.myfaces.custom.jsvalueset.AbstractHtmlJsValueSet"
 *   tagClass = "org.apache.myfaces.custom.jsvalueset.HtmlJsValueSetTag"
 * @JSFJspProperty name = "id" tagExcluded = "true"
 * @JSFJspProperty name = "binding" tagExcluded = "true"
 * @JSFJspProperty name = "rendered" tagExcluded = "true"
 * @JSFJspProperty name = "converter" tagExcluded = "true"
 * @author Martin Marinschek (latest modification by $Author$)
 * @version $Revision$ $Date$
 */
public abstract class AbstractHtmlJsValueSet extends UIOutput
{

    public static final String COMPONENT_TYPE = "org.apache.myfaces.HtmlJsValueSet";
    public static final String COMPONENT_FAMILY = "javax.faces.Output";
    private static final String DEFAULT_RENDERER_TYPE = "org.apache.myfaces.JsValueSet";

    /**
     * @JSFProperty
     */
    public abstract String getName();

    public boolean isRendered()
    {
        if (!UserRoleUtils.isVisibleOnUserRole(this)) return false;
        return super.isRendered();
    }

}


