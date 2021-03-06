package com.wixpress.fjarr.validation;

import com.wixpress.fjarr.server.*;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;

/**
 * @author alex
 * @since 12/23/12 10:23 PM
 *        <p/>
 *        Example implementation of Spring Validation support for Fjarr RPC Server lifecycle
 */
public class SpringValidatorRpcEventHandler extends BaseRpcRequestLifecycleEventHandler
{

    private final Validator validator;

    public SpringValidatorRpcEventHandler(Validator validator)
    {
        this.validator = validator;
    }


    @Override
    public LifecycleEventFlow handleRpcInvocationMethodResolved(ParsedRpcRequest request, RpcResponse response, RpcInvocation invocation)
    {

        for (Object arg : invocation.getResolvedParameters())
        {
            if (arg != null)
            {
                BindingResult bindingResult = new BeanPropertyBindingResult(arg, arg.getClass().getSimpleName());
                validator.validate(arg, bindingResult);

                if (bindingResult.hasErrors())
                {
                    return LifecycleEventFlow.raise(new ValidationException(bindingResult));
                }
            }
        }

        return LifecycleEventFlow.proceed();
    }
}
