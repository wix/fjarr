package com.wixpress.hoopoe.rpc.json;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.wixpress.framework.rpc.server.exceptions.BadRequestException;
import com.wixpress.framework.rpc.server.exceptions.HttpMethodNotAllowedException;
import com.wixpress.framework.rpc.server.exceptions.MethodNotFoundException;
import com.wixpress.framework.rpc.server.exceptions.UnsupportedContentTypeException;
import com.wixpress.hoopoe.reflection.parameters.AnnotationParameterNameDiscoverer;
import com.wixpress.hoopoe.reflection.parameters.ParameterNameDiscoverer;
import com.wixpress.hoopoe.rpc.server.RpcProtocol;
import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author alexeyr
 * @author DanielS
 * @since 6/2/11 10:38 AM
 */
public class JsonRpcProtocol implements RpcProtocol
{
    public static final String CONTENT_TYPE = "application/json-rpc";
    public static final String CHARSET_UTF8 = "charset=UTF-8";
    public static final String RESPONSE_CONTENT_TYPE = CONTENT_TYPE + "; " + CHARSET_UTF8;

    protected static final String JSON_RPC_VERSION = "2.0";
    protected static final String NOTIFICATION = "notification";
    protected static final String ID = "id";

    ObjectReader objectReader;
    ObjectMapper mapper;
    ParameterNameDiscoverer parameterNameDiscoverer = new AnnotationParameterNameDiscoverer();
    public static final List<String> allowedMethods = ImmutableList.of("POST");

    public JsonRpcProtocol(ObjectMapper mapper, ParameterNameDiscoverer parameterNameDiscoverer)
    {
        this.objectReader = mapper.reader().without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.mapper = mapper;
        this.parameterNameDiscoverer = parameterNameDiscoverer;

//        configureObjectMapper(this.mapper);

//        StdTypeResolverBuilder r = new StdTypeResolverBuilder();
//        r.init(JsonTypeInfo.Id.MINIMAL_CLASS, null);
//        r.inclusion(JsonTypeInfo.As.PROPERTY);
//        mapper.setDefaultTyping(r);
//        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, JsonTypeInfo.As.PROPERTY);

    }

    @Override
    public RpcRequest parseRequest(HttpServletRequest request) throws HttpMethodNotAllowedException, UnsupportedContentTypeException, IOException, BadRequestException
    {
        InputStream is = null;
        try
        {
            //application/json-rpc; charset=UTF-8
            if (request.getContentType() == null || !request.getContentType().split(";")[0].equalsIgnoreCase(CONTENT_TYPE))
                throw new UnsupportedContentTypeException(CONTENT_TYPE);

            if (!allowedMethods.contains(request.getMethod()))
                throw new HttpMethodNotAllowedException(allowedMethods);

            is = request.getInputStream();

            return parseRequestJson(mapper.readValue(is, JsonNode.class));
        }
        catch (JsonParseException e)
        {
            throw new BadRequestException("JSON-RPC request payload parse error", e);
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }

    @Override
    public void resolveMethod(List<Method> methods, RpcInvocation invocation, RpcRequest request)
    {
        // TODO - cache resolved method and parameter types

        RpcParameters<?> parameters = invocation.getParameters();
        if (parameters instanceof PositionalRpcParameters)
            resolveMethodPositional(methods, invocation, request);
        else if (parameters instanceof NamedRpcParameters)
            resolveMethodNamed(methods, invocation, request);
    }

    protected void resolveMethodNamed(List<Method> methods, RpcInvocation invocation, RpcRequest request)
    {
        try
        {
            Map<String, Object> params = ((NamedRpcParameters) invocation.getParameters()).getParameters();
            for (Method method : methods)
            {

                List<JsonNode> nonResolvedParams = new ArrayList<JsonNode>(params.size());
                for (String paramName : parameterNameDiscoverer.getParameterNames(method))
                {
                    if (paramName == null)
                    {
                        nonResolvedParams = null;
                        break;
                    }

                    if (params.containsKey(paramName))
                    {
                        nonResolvedParams.add((JsonNode) params.get(paramName));
                    }
                }
                // this resolvedMethod is the correct one
                if (nonResolvedParams != null)
                {
                    Object[] convertedParams = new Object[nonResolvedParams.size()];
                    Type[] parameterTypes = method.getGenericParameterTypes();

                    // convert the parameters
                    for (int i = 0; i < parameterTypes.length; i++)
                    {
                        if (nonResolvedParams.get(i).get("@class") != null)
                            convertedParams[i] = objectReader.readValue(objectReader.treeAsTokens(nonResolvedParams.get(i)), Object.class);
                        else
                            convertedParams[i] = objectReader.readValue(objectReader.treeAsTokens(nonResolvedParams.get(i)), mapper.getTypeFactory().constructType(parameterTypes[i]));
                    }
                    invocation.setResolvedMethod(method);
                    invocation.setResolvedParameters(convertedParams);
                    return;
                }
            }
        }
        catch (IOException e)
        {
            invocation.setError(new MethodNotFoundException("Error at resolving of method [%s] \n %s", e, invocation.getMethodName(), request.getRawRequestBody()));
            return;
        }
        invocation.setError(new MethodNotFoundException("Method [%s] was not found \n %s", invocation.getMethodName(), request.getRawRequestBody()));
    }

    protected void resolveMethodPositional(List<Method> methods, RpcInvocation invocation, RpcRequest request)
    {
        Object[] parameters = ((PositionalRpcParameters) invocation.getParameters()).getParameters();
        try
        {
            for (Method method : methods)
            {
                if (parameters.length == method.getParameterTypes().length)
                {

                    Object[] convertedParams = new Object[parameters.length];
                    Type[] parameterTypes = method.getGenericParameterTypes();

                    // convert the parameters
                    for (int i = 0; i < parameterTypes.length; i++)
                    {
                        // if the client have sent a type info as a @class property - try to use it
                        JsonNode parameter = (JsonNode) parameters[i];
                        if (parameter.get("@class") != null)
                            convertedParams[i] = objectReader.readValue(objectReader.treeAsTokens(parameter), Object.class);
                        else
                            convertedParams[i] = objectReader.readValue(objectReader.treeAsTokens(parameter), mapper.getTypeFactory().constructType(parameterTypes[i]));
                    }
                    invocation.setResolvedMethod(method);
                    invocation.setResolvedParameters(convertedParams);
                    return;
                }
            }
        }
        catch (IOException e)
        {
            invocation.setError(new MethodNotFoundException("Error at resolving of method [%s] \n %s", e, invocation.getMethodName(), request.getRawRequestBody()));
            return;
        }
        invocation.setError(new MethodNotFoundException("Method [%s] was not found \n %s", invocation.getMethodName(), request.getRawRequestBody()));
    }


    @Override
    public void writeResponse(HttpServletResponse response, RpcRequest request) throws IOException
    {

        JsonNode resp = createResponseObject(request);
        response.setContentType(RESPONSE_CONTENT_TYPE);

        ObjectWriter writer = request.getQueryParams().containsKey("prettyPrint")
                ? mapper.writerWithDefaultPrettyPrinter()
                : mapper.writer();

        OutputStream os = null;
        try
        {
            os = response.getOutputStream();
            writer.writeValue(os, resp);
        }
        finally
        {
            IOUtils.closeQuietly(os);
        }
    }

    protected JsonNode createResponseObject(RpcRequest request)
    {
        JsonNode resp = null;
        if (request.getInvocations().size() > 1)
        {
            resp = mapper.createArrayNode();
            for (RpcInvocation invocation : request.getInvocations())
            {
                if (invocation.isError() || invocation.getValueFromContext(NOTIFICATION).equals(false))
                    ((ArrayNode) resp).add(createSingleResponse(invocation));
            }
        }
        else if (request.getInvocations().size() == 1)
            resp = createSingleResponse(request.getInvocations().get(0));

        return resp;
    }

    protected ObjectNode createSingleResponse(RpcInvocation invocation)
    {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", JSON_RPC_VERSION);
        resp.put(ID, (JsonNode) invocation.getValueFromContext(ID, null));

        if (invocation.isError())
        {
            resp.put("error", createErrorResponse(invocation));
        }
        else
        {
            resp.put("result", (invocation.getResolvedMethod().getGenericReturnType() != null) ? mapper.valueToTree(invocation.getResult()) : null);
        }
        return resp;
    }

    protected ObjectNode createErrorResponse(RpcInvocation invocation)
    {
        Exception e = invocation.getError();
        ObjectNode error = mapper.createObjectNode();
        error.put("code", calculateErrorCode(invocation));
        error.put("message", getFullErrorMessage(e));
        Class<?>[] checkedExceptionTypes = invocation.getResolvedMethod() != null ? invocation.getResolvedMethod().getExceptionTypes() : null;


        error.put("data", mapper.valueToTree(e));

//        boolean isCheckedException = false;
//        if (checkedExceptionTypes != null)
//        {
//            for (int i = 0; i < checkedExceptionTypes.length; i++)
//            {
//                if (checkedExceptionTypes[i].equals(e.getClass()))
//                {
//                    error.put("data", mapper.valueToTree(e));
//                    isCheckedException = true;
//                    break;
//                }
//            }
//        }
//        if (!isCheckedException)
//        {
//            ObjectNode data = mapper.createObjectNode();
//            data.put("@class", e.getClass().getCanonicalName());
//            data.put("message", getFullErrorMessage(e));
//            error.put("data", data);
//        }

        return error;
    }

    private String getFullErrorMessage(Throwable e)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage());
        while (e.getCause() != null && e.getCause() != e)
        {
            sb.append("\n  caused by ").append(e.getCause().getClass().getName()).append(", with message: ").append(e.getCause().getMessage());
            e = e.getCause();
        }
        return sb.toString();
    }

    protected int calculateErrorCode(RpcInvocation invocation)
    {
        if (invocation.getErrorCode() != null && !ErrorCodes.clashesWithJsonError(invocation.getErrorCode()))
            return invocation.getErrorCode();
        Class<? extends Exception> clazz = invocation.getError().getClass();

        if (clazz.equals(InvalidJsonRpcRequest.class))
            return ErrorCodes.INVALID_REQUEST;
        if (clazz.equals(MethodNotFoundException.class))
            return ErrorCodes.METHOD_NOT_FOUND;

        return ErrorCodes.INTERNAL_ERROR;
    }


    protected RpcRequest parseRequestJson(JsonNode request) throws BadRequestException
    {
        // process single request
        if (request.isObject())
            return RpcRequest.of(parseSingleRequest((ObjectNode) request));
        else if (request.isArray())// process batch request
            return processBatchRequest((ArrayNode) request);
        else
            throw new BadRequestException("Unsupported request object type " + request.getClass().getName());
    }

    protected RpcRequest processBatchRequest(ArrayNode requests)
    {
        RpcRequest ret = new RpcRequest();
        if (requests.size() == 0)
            ret.getInvocations().add(new RpcInvocation(new InvalidJsonRpcRequest("Request is an empty array ")));
        for (JsonNode request : requests)
        {
            if (!request.isObject())
                ret.getInvocations().add(new RpcInvocation(new InvalidJsonRpcRequest("Unsupported request object type " + request.getClass().getName())));
            else
                ret.getInvocations().add(parseSingleRequest((ObjectNode) request));
        }
        return ret;
    }

    protected RpcInvocation parseSingleRequest(ObjectNode request)
    {
        // validate request
        if (!request.has("jsonrpc"))
            return new RpcInvocation(new InvalidJsonRpcRequest("Missing jsonrpc param"));
        if (!request.has("method"))
            return new RpcInvocation(new InvalidJsonRpcRequest("Missing resolvedMethod param"));
        if (!request.get("method").isTextual())
            return new RpcInvocation(new InvalidJsonRpcRequest("Method name must be a string"));

        // parse request
        String version = request.get("jsonrpc").asText();
        String methodName = request.get("method").asText();
        JsonNode id = null;
        boolean isNotification = false;
        if (request.has(ID))
            id = request.get(ID);
        else
            isNotification = true;
        JsonNode params = request.get("params");

        // more validations
        if (!version.equals(JSON_RPC_VERSION))
            return new RpcInvocation(new InvalidJsonRpcRequest("Missing resolvedMethod param"));

        RpcParameters<?> parameters = buildParameters(params);
        if (parameters == null)
            return new RpcInvocation(new InvalidJsonRpcRequest("Failed parsing resolvedMethod params"));

        return new RpcInvocation(methodName, parameters)
                .withContextValue(ID, id)
                .withContextValue(NOTIFICATION, isNotification);
    }

    protected RpcParameters<?> buildParameters(JsonNode params)
    {
        // handle param arrays, or no params
        if (params.size() == 0 || params.isArray())
        {
            JsonNode[] ret = new JsonNode[params.size()];
            for (int i = 0; i < params.size(); i++)
            {
                ret[i] = params.get(i);
            }

            return new PositionalRpcParameters(ret);
        }
        else if (params.isObject())
        {
            // handle named params
            Map<String, Object> ret = new HashMap<String, Object>(params.size());
            Iterator<String> fieldNames = params.fieldNames();
            while (fieldNames.hasNext())
            {
                String fieldName = fieldNames.next();
                ret.put(fieldName, params.get(fieldName));
            }
            return new NamedRpcParameters(ret);
        }
        return null;
    }

    static class InvalidJsonRpcRequest extends Exception
    {
        protected InvalidJsonRpcRequest(String validation)
        {
            super(validation);
        }
    }

    protected static class ErrorCodes
    {
        public static final int PARSE_ERROR = -32700;      // Parse error Invalid JSON was received by the server.
        // An error occurred on the server while parsing the JSON text.
        public static final int INVALID_REQUEST = -32600;   // The JSON sent is not a valid Request object.
        public static final int METHOD_NOT_FOUND = -32601;  // The resolvedMethod does not exist / is not available.
        public static final int IVALID_PARAMS = -32602;     // Invalid resolvedMethod parameter(s).
        public static final int INTERNAL_ERROR = -32603;    // Internal JSON-RPC error.

        public static boolean clashesWithJsonError(int errorCode)
        {
            return errorCode >= 32600 && errorCode <= 32700;
        }
    }

    public void setMapper(ObjectMapper mapper)
    {
        this.mapper = mapper;
    }
}
