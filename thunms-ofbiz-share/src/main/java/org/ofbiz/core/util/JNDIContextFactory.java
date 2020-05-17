/*
 * $Id: JNDIContextFactory.java,v 1.1 2005/04/01 05:58:06 sfarquhar Exp $
 *
 *  Copyright (c) 2001, 2002 The Open For Business Project - www.ofbiz.org
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a
 *  copy of this software and associated documentation files (the "Software"),
 *  to deal in the Software without restriction, including without limitation
 *  the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *  and/or sell copies of the Software, and to permit persons to whom the
 *  Software is furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included
 *  in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 *  OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 *  IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 *  CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 *  OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 *  THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ofbiz.core.util;

import org.ofbiz.core.config.GenericConfigException;
import org.ofbiz.core.config.JNDIConfigUtil;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Hashtable;

/**
 * JNDIContextFactory - central source for JNDI Contexts by helper name
 *
 * @author <a href="mailto:jonesde@ofbiz.org">David E. Jones</a>
 * @author <a href="mailto:jaz@ofbiz.org">Andy Zeneski</a>
 * @version $Revision: 1.1 $
 * @since 2.0
 */
public class JNDIContextFactory {
    static UtilCache<String, InitialContext> contexts = new UtilCache<String, InitialContext>("entity.JNDIContexts", 0, 0);

    /**
     * Return the initial context according to the entityengine.xml parameters that correspond to the given prefix
     *
     * @return the JNDI initial context
     */
    public static InitialContext getInitialContext(String jndiServerName) throws GenericConfigException {
        InitialContext ic = contexts.get(jndiServerName);

        if (ic == null) {
            synchronized (JNDIContextFactory.class) {
                ic = contexts.get(jndiServerName);

                if (ic == null) {
                    JNDIConfigUtil.JndiServerInfo jndiServerInfo = JNDIConfigUtil.getJndiServerInfo(jndiServerName);

                    if (jndiServerInfo == null) {
                        throw new GenericConfigException("ERROR: no jndi-server definition was found with the name " + jndiServerName + " in jndiservers.xml");
                    }

                    try {
                        if (UtilValidate.isEmpty(jndiServerInfo.contextProviderUrl)) {
                            ic = new InitialContext();
                        } else {
                            Hashtable<String, Object> h = new Hashtable<String, Object>();

                            h.put(Context.INITIAL_CONTEXT_FACTORY, jndiServerInfo.initialContextFactory);
                            h.put(Context.PROVIDER_URL, jndiServerInfo.contextProviderUrl);
                            if (jndiServerInfo.urlPkgPrefixes != null && jndiServerInfo.urlPkgPrefixes.length() > 0)
                                h.put(Context.URL_PKG_PREFIXES, jndiServerInfo.urlPkgPrefixes);

                            if (jndiServerInfo.securityPrincipal != null && jndiServerInfo.securityPrincipal.length() > 0)
                                h.put(Context.SECURITY_PRINCIPAL, jndiServerInfo.securityPrincipal);
                            if (jndiServerInfo.securityCredentials != null && jndiServerInfo.securityCredentials.length() > 0)
                                h.put(Context.SECURITY_CREDENTIALS, jndiServerInfo.securityCredentials);

                            ic = new InitialContext(h);
                        }
                    } catch (Exception e) {
                        String errorMsg = "Error getting JNDI initial context for server name " + jndiServerName;

                        Debug.logError(e, errorMsg);
                        throw new GenericConfigException(errorMsg, e);
                    }

                    if (ic != null) {
                        contexts.put(jndiServerName, ic);
                    }
                }
            }
        }

        return ic;
    }

    /**
     * Removes an entry from the JNDI cache.
     *
     * @param jndiServerName
     */
    public static void clearInitialContext(String jndiServerName) {
        InitialContext ic = contexts.get(jndiServerName);
        if (ic != null)
            contexts.remove(jndiServerName);
    }

}
