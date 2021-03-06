package com.wixpress.fjarr.it.springmvc;

import com.wixpress.fjarr.client.RpcClientProtocol;
import com.wixpress.fjarr.client.RpcInvoker;
import com.wixpress.fjarr.client.exceptions.RpcInvocationException;
import com.wixpress.fjarr.example.InputDTO;
import com.wixpress.fjarr.it.BaseJsonContractTest;
import com.wixpress.fjarr.it.util.ITSpringServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.wixpress.fjarr.client.factory.HttpComponentsInvokerFactory.aDefaultHttpComponentsInvoker;
import static com.wixpress.fjarr.json.factory.JsonRPCClientProtocolFactory.aDefaultJsonRpcClientProtocol;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author alex
 * @since 1/6/13 5:29 PM
 */

public class SpringMvcWithHttpClientTest extends BaseJsonContractTest {

    @BeforeClass
    public static void init() throws Exception {
        server = new ITSpringServer(SERVER_PORT, ITServerConfig.class);
        server.start();
    }

    @AfterClass
    public static void fini() throws Exception {
        server.stop();
    }


    @Test
    public void testValidationSuccess() {
        service.withInputThatNeedsValidation(new InputDTO(""));
    }

    @Test
    public void testValidationFailure() {
        try {
            service.withInputThatNeedsValidation(new InputDTO());
        } catch (RpcInvocationException e) {
            assertThat(e.getMessage(), is("JSON-RPC Error -32603: \"Validation failed\""));
        }
    }

    @Override
    protected RpcClientProtocol anRpcProtocol() {
        return aDefaultJsonRpcClientProtocol();
    }

    @Override
    protected RpcInvoker anRpcInvoker() {
        return aDefaultHttpComponentsInvoker();
    }
}
