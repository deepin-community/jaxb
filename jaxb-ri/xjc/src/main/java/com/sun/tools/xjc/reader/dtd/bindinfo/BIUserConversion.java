/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.tools.xjc.reader.dtd.bindinfo;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JPrimitiveType;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.tools.xjc.model.CAdapter;
import com.sun.tools.xjc.model.CBuiltinLeafInfo;
import com.sun.tools.xjc.model.TypeUse;
import com.sun.tools.xjc.model.TypeUseFactory;

import com.sun.xml.bind.v2.util.XmlFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * {@code <conversion>} declaration in the binding file.
 * This declaration declares a conversion by user-specified methods.
 */
public class BIUserConversion implements BIConversion
{
    /**
     * Wraps a given {@code <conversion>} element in the binding file.
     */
    BIUserConversion( BindInfo bi, Element _e ) {
        this.owner = bi;
        this.e = _e;
    }
    
    private static void add( Map<String,BIConversion> m, BIConversion c ) {
        m.put( c.name(), c );
    }
    
    /** Adds all built-in conversions into the given map. */
    static void addBuiltinConversions( BindInfo bi, Map<String,BIConversion> m ) {
        add( m, new BIUserConversion( bi, parse("<conversion name='boolean' type='java.lang.Boolean' parse='getBoolean' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='byte' type='java.lang.Byte' parse='parseByte' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='short' type='java.lang.Short' parse='parseShort' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='int' type='java.lang.Integer' parse='parseInt' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='long' type='java.lang.Long' parse='parseLong' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='float' type='java.lang.Float' parse='parseFloat' />")));
        add( m, new BIUserConversion( bi, parse("<conversion name='double' type='java.lang.Double' parse='parseDouble' />")));
    }

    private static Element parse(String text) {
        try {
            //this is parsing well known schemas, do not configure secure processing - always true
            DocumentBuilderFactory dbf = XmlFactory.createDocumentBuilderFactory(false);
            InputSource is = new InputSource(new StringReader(text));
            return dbf.newDocumentBuilder().parse(is).getDocumentElement();
        } catch (SAXException x) {
            throw new Error(x);
        } catch (IOException x) {
            throw new Error(x);
        } catch (ParserConfigurationException x) {
            throw new Error(x);
        }
    }


    /** The owner {@link BindInfo} object to which this object belongs. */
    private final BindInfo owner;
    
    /** {@code <conversion>} element which this object is wrapping. */
    private final Element e;



    /** Gets the location where this declaration is declared. */
    public Locator getSourceLocation() {
        return DOMLocator.getLocationInfo(e);
    }
    
    /** Gets the conversion name. */
    public String name() { return DOMUtil.getAttribute(e,"name"); }
    
    /** Gets a transducer for this conversion. */
    public TypeUse getTransducer() {
        
        String ws = DOMUtil.getAttribute(e,"whitespace");
        if(ws==null)    ws = "collapse";

        String type = DOMUtil.getAttribute(e,"type");
        if(type==null)  type=name();
        JType t=null;

        int idx = type.lastIndexOf('.');
        if(idx<0) {
            // no package name is specified.
            try {
                t = JPrimitiveType.parse(owner.codeModel,type);
            } catch( IllegalArgumentException ex ) {
                // otherwise treat it as a class name in the current package
                type = owner.getTargetPackage().name()+'.'+type;
            }
        }
        if(t==null) {
            try {
                // TODO: revisit this later
                JDefinedClass cls = owner.codeModel._class(type);
                cls.hide();
                t = cls;
            } catch( JClassAlreadyExistsException ex ) {
                t = ex.getExistingClass();
            }
        }

        String parse = DOMUtil.getAttribute(e,"parse");
        if(parse==null)  parse="new";

        String print = DOMUtil.getAttribute(e,"print");
        if(print==null)  print="toString";

        JDefinedClass adapter = generateAdapter(owner.codeModel, parse, print, t.boxify());

        // XmlJavaType customization always converts between string and an user-defined type.
        return TypeUseFactory.adapt(CBuiltinLeafInfo.STRING,new CAdapter(adapter));
    }

    // TODO: anyway to reuse this code between XML Schema compiler?
    private JDefinedClass generateAdapter(JCodeModel cm, String parseMethod, String printMethod, JClass inMemoryType) {
        JDefinedClass adapter = null;

        int id = 1;
        while(adapter==null) {
            try {
                JPackage pkg = owner.getTargetPackage();
                adapter = pkg._class("Adapter"+id);
            } catch (JClassAlreadyExistsException ex) {
                // try another name in search for an unique name.
                // this isn't too efficient, but we expect people to usually use
                // a very small number of adapters.
                id++;
            }
        }

        adapter._extends(cm.ref(XmlAdapter.class).narrow(String.class).narrow(inMemoryType));

        JMethod unmarshal = adapter.method(JMod.PUBLIC, inMemoryType, "unmarshal");
        JVar $value = unmarshal.param(String.class, "value");

        JExpression inv;

        if( parseMethod.equals("new") ) {
            // "new" indicates that the constructor of the target type
            // will do the unmarshalling.

            // RESULT: new <type>()
            inv = JExpr._new(inMemoryType).arg($value);
        } else {
            int idx = parseMethod.lastIndexOf('.');
            if(idx<0) {
                // parseMethod specifies the static method of the target type
                // which will do the unmarshalling.

                // because of an error check at the constructor,
                // we can safely assume that this cast works.
                inv = inMemoryType.staticInvoke(parseMethod).arg($value);
            } else {
                inv = JExpr.direct(parseMethod+"(value)");
            }
        }
        unmarshal.body()._return(inv);


        JMethod marshal = adapter.method(JMod.PUBLIC, String.class, "marshal");
        $value = marshal.param(inMemoryType,"value");

        int idx = printMethod.lastIndexOf('.');
        if(idx<0) {
            // printMethod specifies a method in the target type
            // which performs the serialization.

            // RESULT: <value>.<method>()
            inv = $value.invoke(printMethod);
        } else {
            // RESULT: <className>.<method>(<value>)
            inv = JExpr.direct(printMethod+"(value)");
        }
        marshal.body()._return(inv);

        return adapter;
    }
}
