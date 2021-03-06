package com.wixpress.fjarr.it.factories;

import com.wixpress.fjarr.client.NettyClientConfig;
import com.wixpress.fjarr.client.NettyInvoker;

/**
 * @author: ittaiz
 * @since: 5/29/13
 */
public class NettyInvokerFactory {
    public static NettyInvoker aDefaultNettyInvoker() {
        return aNettyInvokerFrom(NettyClientConfig.defaults());
    }

    public static NettyInvoker aNettyInvokerFrom(NettyClientConfig config) {
        return new NettyInvoker(config);
    }
}
