/*
 * Copyright (C) 2020  Hugo JOBY
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License v3 for more details.
 *
 * You should have received a copy of the GNU Lesser General Public v3
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package com.castsoftware.demeter.controllers.sate;

import com.castsoftware.demeter.config.Configuration;
import com.castsoftware.demeter.database.Neo4jAL;
import com.castsoftware.demeter.exceptions.neo4j.Neo4jBadRequestException;
import com.castsoftware.demeter.exceptions.neo4j.Neo4jNoResult;
import com.castsoftware.demeter.exceptions.neo4j.Neo4jQueryException;
import com.castsoftware.demeter.models.demeter.OperationNode;
import com.castsoftware.demeter.models.demeter.SaveNode;
import com.castsoftware.demeter.models.imaging.Level5Node;
import org.neo4j.graphdb.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class StateController {

    // Static Imaging properties
    private static final String AGGREGATES_REL = Configuration.get("imaging.node.level_nodes.links");
    private static final String IMAGING_OBJECT_LABEL = Configuration.get("imaging.node.object.label");
    private static final String IMAGING_OBJECT_FULL_NAME = Configuration.get("imaging.node.object.fullName");
    private static final String GENERATED_LEVEL_PREFIX = Configuration.get("demeter.prefix.generated_level_prefix");

    /**
     * Save demeter level to the database
     *
     * @param neo4jAL            Neo4j Access Layer
     * @param applicationContext Name of the application concerned by the save
     * @param saveName           Name of the save
     * @throws Neo4jQueryException
     * @throws Neo4jNoResult
     */
    public static int saveDemeterLevel5(Neo4jAL neo4jAL, String applicationContext, String saveName) throws Neo4jQueryException, Neo4jNoResult {
        Label objectLabel = Label.label(IMAGING_OBJECT_LABEL);
        RelationshipType toSaveRel = RelationshipType.withName(OperationNode.getRelationToSaveNode());
        RelationshipType aggregates = RelationshipType.withName(AGGREGATES_REL);

        int savedObj = 0;

        // Get all the demeter
        Map<String, String[]> mapLevelFullName = new HashMap<>();

        // Create Save node in the database
        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
        String strDate = dateFormat.format(date);

        SaveNode svn = new SaveNode(neo4jAL, saveName, applicationContext, strDate);
        Node saveNode = svn.createNode();

        String fullName;
        for (Level5Node level : Level5Node.getAllNodesByApplication(neo4jAL, applicationContext)) {
            fullName = level.getFullName();

            // If the full name match, the level was generated by Demeter
            if (fullName.matches(".*##(" + GENERATED_LEVEL_PREFIX + ".*)")) {

                Node n = level.getNode();

                // Get connected nodes
                List<String> fullNameList = new ArrayList<>();
                for (Iterator<Relationship> relIt = n.getRelationships(Direction.OUTGOING, aggregates).iterator(); relIt.hasNext(); ) {
                    Node obj = relIt.next().getEndNode();
                    if (obj.hasLabel(objectLabel) && obj.hasProperty(IMAGING_OBJECT_FULL_NAME)) {
                        fullNameList.add((String) obj.getProperty(IMAGING_OBJECT_FULL_NAME));
                        savedObj++;
                    }
                }

                // Create operation node
                OperationNode opN = new OperationNode(neo4jAL, level.getName(), fullNameList);
                Node opNode = opN.createNode();

                // Link the operation node to the Save node
                opNode.createRelationshipTo(saveNode, toSaveRel);
            }
        }

        return savedObj;
    }

    /**
     * Get all save nodes specific to one application
     *
     * @param neo4jAL         Neo4j Access Layer
     * @param applicationName Name of the application
     * @return
     * @throws Neo4jNoResult
     */
    public static List<Node> getSaveNodesByApplication(Neo4jAL neo4jAL, String applicationName) throws Neo4jNoResult {
        List<Node> saveNodes = new ArrayList<>();
        for (SaveNode sv : SaveNode.getAllSaveNodes(neo4jAL)) {
            if (sv.getApplication().equals(applicationName)) {
                try {
                    saveNodes.add(sv.getNode());
                } catch (Neo4jQueryException e) {
                    neo4jAL.logError(String.format("Save node with name '%s' produced an error.", sv.getName()));
                }
            }
        }
        return saveNodes;
    }

    /**
     * Get all the Saves in the database
     *
     * @param neo4jAL Neo4j Access Layer
     * @return The list of nodes
     * @throws Neo4jNoResult
     */
    public static List<Node> getAllSaveNodes(Neo4jAL neo4jAL) throws Neo4jNoResult {
        List<Node> saveNodes = new ArrayList<>();
        for (SaveNode sv : SaveNode.getAllSaveNodes(neo4jAL)) {
            try {
                saveNodes.add(sv.getNode());
            } catch (Neo4jQueryException e) {
                neo4jAL.logError(String.format("Save node with name '%s' produced an error.", sv.getName()));
            }
        }
        return saveNodes;
    }

    /**
     * Remove a specific save node from the database. Find the save by its name
     *
     * @param neo4jAL  Neo4j access layer
     * @param saveName Name of the save
     * @return true if a save matching this name was found, false otherwise
     */
    public static boolean removeSave(Neo4jAL neo4jAL, String saveName) throws Neo4jNoResult, Neo4jBadRequestException, Neo4jQueryException {
        for (SaveNode sv : SaveNode.getAllSaveNodes(neo4jAL)) {
            if (sv.getName().equals(saveName)) {
                sv.deleteNode();
                return true;
            }
        }

        return false;
    }

    /**
     * Remove all saves from the database
     *
     * @param neo4jAL
     * @return Number of Saves removed during the process
     * @throws Neo4jNoResult
     * @throws Neo4jBadRequestException
     * @throws Neo4jQueryException
     */
    public static int removeAllSaves(Neo4jAL neo4jAL) throws Neo4jNoResult, Neo4jBadRequestException, Neo4jQueryException {
        int numDeleted = 0;
        for (SaveNode sv : SaveNode.getAllSaveNodes(neo4jAL)) {
            sv.deleteNode();
            numDeleted++;
        }

        return numDeleted;
    }

}
