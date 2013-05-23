/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.rest.api.process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.ActivitiIllegalArgumentException;
import org.activiti.engine.runtime.Execution;
import org.activiti.rest.api.ActivitiUtil;
import org.activiti.rest.api.engine.variable.RestVariable;
import org.activiti.rest.api.engine.variable.RestVariable.RestVariableScope;
import org.activiti.rest.application.ActivitiRestServicesApplication;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;


/**
 * @author Frederik Heremans
 */
public class ExecutionVariableCollectionResource extends BaseExecutionVariableResource {

  @Get
  public List<RestVariable> getVariables() {
    if (authenticate() == false)
      return null;
    
    Execution execution = getExecutionFromRequest();
    
    List<RestVariable> result = new ArrayList<RestVariable>();
    Map<String, RestVariable> variableMap = new HashMap<String, RestVariable>();
    
    // Check if it's a valid execution to get the variables for
    RestVariableScope variableScope = RestVariable.getScopeFromString(getQueryParameter("scope", getQuery()));
    
    if(variableScope == null) {
      // Use both local and global variables
      addLocalVariables(execution, variableMap);
      addGlobalVariables(execution, variableMap);
    } else if(variableScope == RestVariableScope.GLOBAL) {
      addGlobalVariables(execution, variableMap);
    } else if(variableScope == RestVariableScope.LOCAL) {
      addLocalVariables(execution, variableMap);
    }
    
    // Get unique variables from map
    result.addAll(variableMap.values());
    return result;
  }
  
  @Post
  public Object createExecutionVariable(Representation representation) {
    if (authenticate() == false)
      return null;
    
    Execution execution = getExecutionFromRequest();
    
    Object result = null;
    if(MediaType.MULTIPART_FORM_DATA.isCompatible(representation.getMediaType())) {
      result = setBinaryVariable(representation, execution, true);
    } else {
      // Since we accept both an array of RestVariables and a single RestVariable, we need to inspect the
      // body before passing on to the converterService
      try {
        List<RestVariable> variables = new ArrayList<RestVariable>();
        result = variables;
        
        RestVariable[] restVariables = getConverterService().toObject(representation, RestVariable[].class, this);
        if(restVariables == null || restVariables.length == 0) {
          throw new ActivitiIllegalArgumentException("Request didn't cantain a list of variables to create.");
        }
        for(RestVariable var : restVariables) {
          variables.add(setSimpleVariable(var, execution, true));
        }
      } catch (IOException ioe) {
        throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, ioe);
      }
    }
    setStatus(Status.SUCCESS_CREATED);
    return result;
  }
  
  @Delete
  public void deleteAllLocalVariables() {
    Execution execution = getExecutionFromRequest();
    Collection<String> currentVariables = ActivitiUtil.getRuntimeService().getVariablesLocal(execution.getId()).keySet();
    ActivitiUtil.getRuntimeService().removeVariablesLocal(execution.getId(), currentVariables);
    
    setStatus(Status.SUCCESS_NO_CONTENT);
  }
  
  protected void addGlobalVariables(Execution execution, Map<String, RestVariable> variableMap) {
    Map<String, Object> rawVariables = ActivitiUtil.getRuntimeService().getVariables(execution.getId());
    List<RestVariable> globalVariables = getApplication(ActivitiRestServicesApplication.class)
            .getRestResponseFactory().createRestVariables(this, rawVariables, null, execution.getId(), null, RestVariableScope.GLOBAL);
    
    // Overlay global variables over local ones. In case they are present the values are not overridden, 
    // since local variables get precedence over global ones at all times.
    for(RestVariable var : globalVariables) {
      if(!variableMap.containsKey(var.getName())) {
        variableMap.put(var.getName(), var);
      }
    }
  }

  
  protected void addLocalVariables(Execution execution, Map<String, RestVariable> variableMap) {
    Map<String, Object> rawLocalvariables = ActivitiUtil.getRuntimeService().getVariablesLocal(execution.getId());
    List<RestVariable> localVariables = getApplication(ActivitiRestServicesApplication.class)
            .getRestResponseFactory().createRestVariables(this, rawLocalvariables, null, execution.getId(), null, RestVariableScope.LOCAL);
    
    for(RestVariable var : localVariables) {
      variableMap.put(var.getName(), var);
    }
  }
  
}
