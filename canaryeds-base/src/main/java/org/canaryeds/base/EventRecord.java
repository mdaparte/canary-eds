/*
 * Copyright 2014 Sandia Corporation.
 * Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation, the U.S.
 * Government retains certain rights in this software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 * This software was written as part of an Inter-Agency Agreement between Sandia
 * National Laboratories and the US EPA NHSRC.
 */
package org.canaryeds.base;

import gov.sandia.seme.framework.DataChannel;
import gov.sandia.seme.framework.DataStatus;
import gov.sandia.seme.framework.Step;
import static java.lang.Math.max;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.simple.JSONValue;

/**
 * Used to store details of an event. These are created continuously by the
 * Station objects and are stored in an array. These are used to create the
 * summary file at the end of a CANARY-EDS batch run, or they can be accessed
 * via the CANARY object's methods.
 *
 * Besides the full object, there are two HashMap representations, one
 * which is a full representation and one which is a summary. The summary map
 * only contains two key:value pairs, the values of which are
 * ArrayLists. The "headers" key contains the names for each of the
 * array list elements; the other key is the event's name, formed as
 * "stationName.stepIndex".
 *
 * @todo Finish documentation for methods in EventRecord
 *
 * @htmlonly
 * @author David Hart, dbhart
 * @endhtmlonly
 */
public class EventRecord {

    String stationName;
    int eventIndex = -1;
    int startIndex = -1;
    int finalIndex = -1;
    int numOutliers = 0;
    boolean hasNonNormalStatus = false;
    boolean completed = false;
    // per channel values
    final ArrayList<DataChannel> channels = new ArrayList();
    final ArrayList<DataChannel> workflowChannels = new ArrayList();
    final ArrayList<String> channelNames = new ArrayList();
    final ArrayList<String> parameters = new ArrayList();
    final ArrayList<String> usages = new ArrayList();
    // per step values
    final ArrayList<Step> steps = new ArrayList();
    final ArrayList<Double> probability = new ArrayList();
    final ArrayList<EventStatus> stationStatus = new ArrayList();
    // per channel per step
    final HashMap<String, ArrayList<Double>> rawData = new HashMap();
    final HashMap<String, ArrayList<Double>> residuals = new HashMap();
    final HashMap<String, ArrayList<Short>> contributed = new HashMap();
    final HashMap<String, ArrayList<Integer>> limitViolation = new HashMap();
    final HashMap<String, ArrayList<DataStatus>> channelStatus = new HashMap();

    public EventRecord(String stationName, ArrayList<DataChannel> dataChannels) {
        this.stationName = stationName;
        this.channels.addAll(dataChannels);
        for (DataChannel channel : this.channels) {
            channelNames.add(channel.getTag());
            parameters.add(channel.getStringOpt("parameter"));
            usages.add(channel.getStringOpt("usage"));
            String name = channel.getName();
            rawData.put(name, new ArrayList<Double>());
            residuals.put(name, new ArrayList<Double>());
            contributed.put(name, new ArrayList<Short>());
            limitViolation.put(name, new ArrayList<Integer>());
            channelStatus.put(name, new ArrayList<DataStatus>());
        }
    }

    public void setWorkflowChannels(ArrayList<DataChannel> workflowChannels) {
        this.workflowChannels.clear();
        this.workflowChannels.addAll(workflowChannels);
    }

    public Map toMap() {
        this.eventIndex = this.steps.get(0).getIndex();
        HashMap map = new HashMap();
        map.put("stationName", stationName);
        map.put("startIndex", +eventIndex);
        map.put("channelNames", channelNames);
        map.put("parameters", parameters);
        map.put("usages", usages);
        map.put("steps", steps);
        map.put("probability", probability);
        map.put("stationStatus", stationStatus);
        map.put("rawData", rawData);
        map.put("residuals", residuals);
        map.put("contributed", contributed);
        map.put("limitViolation", limitViolation);
        map.put("channelStatus", channelStatus);
        return map;
    }

    public Map summarize() {
        if (startIndex >= 0) {
            this.eventIndex = this.steps.get(startIndex).getIndex();
        } else {
            this.eventIndex = this.steps.get(0).getIndex();
        }
        HashMap map = new HashMap();
        String name = stationName + "." + eventIndex;
        ArrayList vals = new ArrayList();
        ArrayList headers = new ArrayList();
        if (startIndex < 0) {
            startIndex = 0;
        }
        vals.add(this.steps.get(startIndex).toString());
        headers.add("start");
        int duration = this.finalIndex - startIndex;
        //map.put("duration",duration);
        vals.add(duration);
        headers.add("duration");
        headers.add("term cause");
        if (finalIndex < 0) {
            finalIndex = this.steps.size();
        }
        EventStatus lastStatus = this.stationStatus.get(
                this.finalIndex - 1);
        if (lastStatus == EventStatus.EVENT_TIMEOUT) {
            //map.put("term cause", "ETO");
            vals.add("ETO");
        } else if (lastStatus == EventStatus.EVENT_IDENTIFIED) {
            //map.put("term cause", "ID");
            vals.add("ID");
        } else {
            //map.put("term cause", "RTN");
            vals.add("RTN");
        }
        ArrayList contrib = new ArrayList();
        headers.add(parameters);
        for (DataChannel channel : channels) {
            String cname = channel.getName();
            Object[] c = contributed.get(cname).toArray();
            int cSum = 0;
            //for (Object v : c) {
            for (int i = max(0, this.startIndex - this.numOutliers); i < finalIndex; i++) {
                Object v = c[i];
                if (((Number) v).intValue() == 1) {
                    cSum += 1;
                }
            }
            contrib.add(cSum);
        }
        vals.add(contrib);
        map.put("headers", headers);
        map.put(name, vals);
        return map;
    }

    @Override
    public String toString() {
        String jsonText = JSONValue.toJSONString(this.toMap());
        return jsonText;
    }

    public void removeFirstElement(int numPreEventSteps) {
        if (this.hasNonNormalStatus || (this.steps.size() <= numPreEventSteps)) {
            return;
        }
        int remvIdx = 0;
        this.steps.remove(remvIdx);
        this.probability.remove(remvIdx);
        this.stationStatus.remove(remvIdx);
        for (DataChannel channel : this.channels) {
            String name = channel.getName();
            this.channelStatus.get(name).remove(remvIdx);
            this.rawData.get(name).remove(remvIdx);
            this.residuals.get(name).remove(remvIdx);
            this.limitViolation.get(name).remove(remvIdx);
            this.contributed.get(name).remove(remvIdx);
        }
    }

    public void setChannelStatuses() {
        for (DataChannel channel : channels) {
            DataStatus status = channel.getStatus();
            this.channelStatus.get(channel.getName()).add(status);
        }
    }

    public void setChannelRawData(Double[] data) {
        int ct = 0;
        int dataIdx = steps.get(steps.size() - 1).getIndex();
        for (DataChannel channel : channels) {
            if (this.workflowChannels.contains(channel)) {
                this.rawData.get(channel.getName()).add(data[ct]);
                ct++;
            } else {
                rawData.get(channel.getName()).add(channel.getDoubleValue(
                        dataIdx));
            }
        }
    }

    public void setChannelLimitViolations(Integer[] violations) {
        int ct = 0;
        for (DataChannel channel : channels) {
            if (this.workflowChannels.contains(channel)) {
                this.limitViolation.get(channel.getName()).add(violations[ct]);
                ct++;
            } else {
                this.limitViolation.get(channel.getName()).add(0);
            }
        }
    }

    public void setChannelContributed(Short[] contributed) {
        int ct = 0;
        for (DataChannel channel : channels) {
            if (this.workflowChannels.contains(channel)) {
                this.contributed.get(channel.getName()).add(contributed[ct]);
                ct++;
            } else {
                this.contributed.get(channel.getName()).add((short)0);
            }
        }
    }

    public void setChannelResiduals(Double[] residuals) {
        int ct = 0;
        for (DataChannel channel : channels) {
            if (this.workflowChannels.contains(channel)) {
                this.residuals.get(channel.getName()).add(residuals[ct]);
                ct++;
            } else {
                this.residuals.get(channel.getName()).add(0.0);
            }
        }

    }

    public void addStep(Step step) {
        this.steps.add(step);
    }

    public void addProbabilityAndStatus(double probability, EventStatus status) {
        this.probability.add(probability);
        this.stationStatus.add(status);
        if (status == EventStatus.OUTLIER_DETECTED) {
            numOutliers++;
            this.finalIndex = this.stationStatus.size();
        }
        if (status == EventStatus.NORMAL || status == EventStatus.CHANNELS_ALARMING) {
            if (this.hasNonNormalStatus && probability < 0.5) {
                this.completed = true;
            } else if (!this.hasNonNormalStatus) {
                numOutliers = 0;
            }
        }
        if (status == EventStatus.EVENT_IDENTIFIED || status == EventStatus.POSSIBLE_EVENT) {
            this.hasNonNormalStatus = true;
            if (this.startIndex < 0) {
                this.startIndex = this.stationStatus.size() - 1;
            }
            this.finalIndex = this.stationStatus.size();
        }
        if (status == EventStatus.EVENT_TIMEOUT) {
            this.finalIndex = this.stationStatus.size();
            this.completed = true;
        }
    }

    public boolean isCompleted() {
        return this.completed;
    }

}
