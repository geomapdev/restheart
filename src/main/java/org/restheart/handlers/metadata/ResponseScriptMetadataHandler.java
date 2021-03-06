/*
 * RESTHeart - the data REST API server
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.metadata;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import javax.script.Bindings;
import javax.script.ScriptException;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.RepresentationTransformer;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import static org.restheart.handlers.metadata.AbstractScriptMetadataHandler.getBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ResponseScriptMetadataHandler extends AbstractScriptMetadataHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(ResponseScriptMetadataHandler.class);

    /**
     * Creates a new instance of ResponseScriptMetadataHandler
     *
     * @param next
     */
    public ResponseScriptMetadataHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    boolean canCollRepresentationTransformersAppy(RequestContext context) {
        return (context.getMethod() == RequestContext.METHOD.GET
                && (context.getType() == RequestContext.TYPE.DOCUMENT || context.getType() == RequestContext.TYPE.COLLECTION)
                && context.getCollectionProps().containsField(RepresentationTransformer.RTLS_ELEMENT_NAME));
    }

    @Override
    boolean canDBRepresentationTransformersAppy(RequestContext context) {
        return (context.getMethod() == RequestContext.METHOD.GET
                && (context.getType() == RequestContext.TYPE.DB || context.getType() == RequestContext.TYPE.COLLECTION)
                && context.getDbProps().containsField(RepresentationTransformer.RTLS_ELEMENT_NAME));
    }

    @Override
    void enforceDbRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException, ScriptException {
        List<RepresentationTransformer> dbRts = RepresentationTransformer.getFromJson(context.getDbProps(), false);

        RequestContext.TYPE requestType = context.getType(); // DB or COLLECTION

        for (RepresentationTransformer rt : dbRts) {
            if (rt.getPhase() == RepresentationTransformer.PHASE.RESPONSE) {
                if (rt.getScope() == RepresentationTransformer.SCOPE.THIS && requestType == RequestContext.TYPE.DB) {
                    rt.evaluate(getBindings(exchange, context, LOGGER));
                } else if (rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && requestType == RequestContext.TYPE.COLLECTION) {
                    BasicDBObject _embedded = (BasicDBObject) context.getResponseContent().get("_embedded");

                    // evaluate the script on children collection
                    BasicDBList colls = (BasicDBList) _embedded.get("rh:coll");

                    if (colls != null) {
                        for (String k : colls.keySet()) {
                            DBObject coll = (DBObject) colls.get(k);

                            Bindings b = getBindings(exchange, context, LOGGER);

                            b.put("$responseContent", coll);

                            rt.evaluate(b);
                        }
                    }
                }
            }
        }
    }

    @Override
    void enforceCollRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException, ScriptException {
        List<RepresentationTransformer> dbRts = RepresentationTransformer.getFromJson(context.getCollectionProps(), false);

        RequestContext.TYPE requestType = context.getType(); // DOCUMENT or COLLECTION

        for (RepresentationTransformer rt : dbRts) {
            if (rt.getPhase() == RepresentationTransformer.PHASE.RESPONSE) {
                if (rt.getScope() == RepresentationTransformer.SCOPE.THIS && requestType == RequestContext.TYPE.COLLECTION) {
                    // evaluate the script on collection
                    rt.evaluate(getBindings(exchange, context, LOGGER));
                } else if (rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && requestType == RequestContext.TYPE.COLLECTION) {
                    BasicDBObject _embedded = (BasicDBObject) context.getResponseContent().get("_embedded");

                    // evaluate the script on children documents
                    BasicDBList docs = (BasicDBList) _embedded.get("rh:doc");

                    if (docs != null) {
                        for (String k : docs.keySet()) {
                            DBObject doc = (DBObject) docs.get(k);

                            Bindings b = getBindings(exchange, context, LOGGER);

                            b.put("$responseContent", doc);

                            rt.evaluate(b);
                        }
                    }
                    
                    // evaluate the script on children files
                    BasicDBList files = (BasicDBList) _embedded.get("rh:file");

                    if (files != null) {
                        for (String k : files.keySet()) {
                            DBObject file = (DBObject) files.get(k);

                            Bindings b = getBindings(exchange, context, LOGGER);

                            b.put("$responseContent", file);

                            rt.evaluate(b);
                        }
                    }
                } else if (rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && requestType == RequestContext.TYPE.DOCUMENT) {
                    // evaluate the script on document
                    rt.evaluate(getBindings(exchange, context, LOGGER));
                }
            }
        }
    }
}
