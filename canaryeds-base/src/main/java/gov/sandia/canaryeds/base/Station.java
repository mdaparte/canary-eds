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
package gov.sandia.canaryeds.base;

import gov.sandia.seme.framework.DataChannel;
import gov.sandia.seme.framework.Descriptor;
import gov.sandia.seme.framework.Message;
import gov.sandia.seme.framework.MessageType;
import gov.sandia.seme.framework.ModelConnection;
import gov.sandia.seme.framework.Step;
import gov.sandia.seme.framework.ConfigurationException;
import gov.sandia.seme.framework.InitializationException;
import gov.sandia.seme.framework.InvalidComponentClassException;
import gov.sandia.seme.util.MessagableImpl;
import static java.lang.Math.max;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.apache.log4j.Logger;

/**
 * @if doxyUser
 * @page userStationDet Configuration Details: Stations The following
 * configuration options are available for
 *
 * @code{.yaml}
 * canary stations:
 *     StationB:
 *        Station:
 *            enabled: true
 *            tagPrefix: StationB
 *            idNumberStation: 1
 *            idNumberLocation: 1
 *            inputs: [stationb_in]
 *            channels: [TEST_CL, TEST_PH, TEST_TEMP, TEST_TURB, TEST_TOC]
 *            outputs: [stationb_out]
 *            workflow: [test]
 * @endcode
 * @endif
 */
/**
 * Provides the monitoring station logical grouping and runs event detection
 * workflows. The logical grouping for event detection in CANARY-EDS, the
 * Station provides the ModelConnection for the underlying SeMe framework.
 *
 * @internal
 * @author dbhart
 * @author $LastChangedBy$
 * @version $Rev$, $Date$
 */
public final class Station extends MessagableImpl implements ModelConnection {

    private static final Logger LOG = Logger.getLogger(Station.class);
    static final long serialVersionUID = -1785562090925337343L;

    private static boolean all(boolean[] values) {
        if (values.length == 0) {
            return true;
        }
        for (int i = 0; i < values.length; i++) {
            if (!values[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean any(boolean[] values) {
        if (values.length == 0) {
            return true;
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                return true;
            }
        }
        return false;
    }

    private static boolean[] clear(boolean[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = false;
        }
        return values;
    }
    /**
     * is the station currently executing.
     */
    public volatile boolean executing = false;

    /**
     * List of events that have been recorded.
     */
    protected final ArrayList<EventRecord> events = new ArrayList();
    /**
     * The configuration object.
     */
    protected Descriptor configDesc;
    /**
     * Access to the station's channels by name.
     */
    protected final HashMap<String, DataChannel> channels;
    /**
     * Access to the station's channels in order.
     */
    protected final ArrayList<DataChannel> channelList;
    /**
     * Is this station enabled for execution.
     */
    protected boolean enabled;
    /**
     * The location ID number.
     *
     */
    protected int locNumber;
    /**
     * The station ID number.
     *
     */
    protected int stnNumber;
    /**
     * Handle to the current event.
     *
     */
    protected EventRecord currentEvent;
    /**
     * The station's last state.
     *
     */
    protected EventStatus lastStatus;
    /**
     * A list of tags that must be seen before workflow execution starts.
     *
     */
    protected ArrayList<String> synchronizeToTags;
    /**
     * The station's tag name (used for message routing).
     *
     */
    protected String tag;
    /**
     * The station's event detection workflows.
     */
    protected Workflow workflow = null;

    public Station(String label, int d) {
        super(label, d);
        this.synchronizeToTags = new ArrayList();
        this.recvdStatusForCurrentStep = new boolean[0];
        this.workflow = null;
        this.channels = new HashMap();
        this.channelList = new ArrayList();
    }

    public Station(String label, int d, Descriptor conf) {
        super(label, d);
        this.synchronizeToTags = new ArrayList();
        this.recvdStatusForCurrentStep = new boolean[0];
        this.channels = new HashMap();
        this.channelList = new ArrayList();
        this.configure(conf);
    }

    public Station(Descriptor conf) {
        super(conf.getName(), 10);
        this.synchronizeToTags = new ArrayList();
        this.recvdStatusForCurrentStep = new boolean[0];
        this.channels = new HashMap();
        this.channelList = new ArrayList();
        this.configure(conf);
    }

    @Override
    public final void configure(Descriptor conf) {
        configDesc = conf;
        tag = conf.getTag();
        LOG.info(
                "Configuring Station'{'name=" + conf.getName() + "'}'");
        LOG.debug(conf.toString());
        HashMap opts = conf.getOptions();
        LOG.debug(opts);
        if (opts.containsKey("stepConfig")) {
            // do step config
        }
        try {
            if (opts.get("channels") instanceof Map) {
                HashMap descChannels = new EDSComponents().getChannelDescriptors((HashMap) opts.get("channels"));
                for ( Object item : descChannels.values()) {
                    configDesc.addToConsumesTags(((Descriptor)item).getTag());
                    configDesc.addToRequiresComponents((Descriptor) item);
                }
            }
            if (opts.get("workflow") instanceof Map) {
                HashMap descWorkflow = new EDSComponents().getWorkflowDescriptors((HashMap) opts.get("workflow"));
                for ( Object item : descWorkflow.values()) {
                    configDesc.addToRequiresComponents((Descriptor) item);
                }
            }
        } catch (ConfigurationException ex) {
            java.util.logging.Logger.getLogger(Station.class.getName()).log(Level.SEVERE, null, ex);
        }
        ArrayList<String> notConsumedYet;
        notConsumedYet = new ArrayList();
        for (String consume : conf.getConsumesTags()) {
            notConsumedYet.add(consume);
            this.addConsumes(consume);
        }//endof for(String in conf.consumes)
        for (String produce : conf.getProducesTags()) {
            this.addProduces(tag);
        }//endof for(String in conf.produces
        this.recvdStatusForCurrentStep = new boolean[this.synchronizeToTags.size()];
        this.recvdStatusForCurrentStep = clear(this.recvdStatusForCurrentStep);
    }// endof method(configure)

    /**
     * Get the list of events.
     */
    public ArrayList<EventRecord> getEvents() {
        return events;
    }

    @Override
    public final void initialize() throws InitializationException {
        Descriptor conf = configDesc;
        int maxWinSize = 50;
        if (conf.getRequiresComponents() == null
                || conf.getRequiresComponents().isEmpty()) {
            throw new InitializationException("Failed to configure station " + this.name + ", no data channels or workflows/algorithms supplied.");
        }
        for (Descriptor reqd : conf.getRequiresComponents()) {
            switch (reqd.getType().toString()) {
                case "SUBCOMPONENT":
                    /*
                     * add the workflow to the station
                     */
                    try {
                        workflow = ((EDSComponents) this.getComponentFactory()).newWorkflow(
                                reqd);
                        maxWinSize = max(maxWinSize,
                                workflow.getMaxWindowNeeded());
                    } catch (ConfigurationException ex) {
                        LOG.fatal("error adding Workflow");
                        throw new InitializationException("Workflow could not be configured: " + ex.getMessage());
                    } catch (InvalidComponentClassException ex) {
                        LOG.fatal("error adding Workflow");
                        throw new InitializationException(ex.getMessage());
                    }// endof try
                    break;// endof case(WORKFLOW)// endof case(WORKFLOW)
            }
        }
        for (Descriptor reqd : conf.getRequiresComponents()) {
            switch (reqd.getType().toString()) {
                case "DATACHANNEL":
                    /*
                     * add the channel to the station
                     */
                    try {
                        HashMap extOpts = reqd.getOptions();
                        extOpts.put("frameSize", maxWinSize);
                        String chanTag = reqd.getTag();
                        DataChannel channel = this.getComponentFactory().newDataChannel(
                                reqd); // runs channel.configure
                        channels.put(chanTag, channel);
                        //notConsumedYet.remove(chanTag);
                    } catch (InvalidComponentClassException ex) {
                        LOG.fatal("error adding DataChannel", ex);
                        throw new InitializationException(ex.getMessage());
                    }// endof try
                    break;// endof case(CHANNEL)
            }// endof switch(descriptor.type)
        }// endof for(descriptor in conf.requires)
        if (workflow == null) {
            LOG.fatal("Station " + name + " has no workflow specified!!!");
            throw new InitializationException("Failed to include a workflow!");
        }
        for (String chanName : channels.keySet()) {
            DataChannel chan = channels.get(chanName);
            for (String reqName : chan.getRequires()) {
                if (channels.get(reqName) == null) {
                    LOG.warn("Channels key set: " + channels.keySet());
                    throw new InitializationException(
                            "Failed to find a required data channel: " + reqName);
                } else {
                    chan.linkChannel(channels.get(reqName));
                }
            }
            channels.get(chanName).initialize();
            workflow.addChannel(channels.get(chanName));
            channelList.add(channels.get(chanName));
        }
        workflow.initialize();
        this.currentEvent = new EventRecord(this.name, channelList);
        this.currentEvent.setWorkflowChannels(workflow.getChannels());
    }

    /**
     * Perform event detection. Read in data, check for per-channel
     * errors/flags, then run the Workflow. Once the Workflow results have been
     * evaluated, run any event identifiers. Return all results in a result
     * Message.
     *
     * @return status message
     */
    @Override
    public int evaluateModel() {
        //LOG.debug("Thread " + this.name + "is executing.");
        /* original MATLAB code
         % case 'external' % NEW JAVA-BASED CANARYS CORE ENGINE
         % for iTau = 1:n_tau
         %   % Set the current information for the algorithm
         %   LOC.algs(iAlg).algJ(iTau).set_calibration_status(b_Calibration);
         %   LOC.algs(iAlg).algJ(iTau).set_current_data(newData);
         %   LOC.algs(iAlg).algJ(iTau).set_current_usable(~isnan(newAlms));
         %   % Run the algorithm
         %   LOC.algs(iAlg).algJ(iTau).evaluate();
         %   try
         %     LOC.algs(iAlg).algJ(iTau).keep_by_rule();
         %   catch ERR
         %     if ~isempty(findstr(ERR.message,'java.lang.UnsupportedOperationException'))
         %       status = LOC.algs(iAlg).algJ(iTau).get_detection_status();
         %       switch char(status)
         %         case {'EVENT','OUTLIER'}
         %           LOC.algs(iAlg).algJ(iTau).keep_nans();
         %         otherwise
         %           LOC.algs(iAlg).algJ(iTau).keep_current();
         %       end
         %     else
         %       rethrow(ERR);
         %     end
         %   end
         %   % Read the results from the Java Algorithm
         %   status = LOC.algs(iAlg).algJ(iTau).get_detection_status();
         %   switch char(status)
         %     case {'EVENT'}
         %       LOC.algs(iAlg).eventcode(idx,:,iTau) = int8(1);
         %     case {'OUTLIER'}
         %       LOC.algs(iAlg).eventcode(idx,:,iTau) = int8(0);
         %     case {'NORMAL'}
         %       LOC.algs(iAlg).eventcode(idx,:,iTau) = int8(0);
         %     case {'MISSINGHIST'}
         %       LOC.algs(iAlg).eventcode(idx,:,iTau) = int8(-2);
         %     case {'CALIBRATION'}
         %       LOC.algs(iAlg).eventcode(idx,:,iTau) = int8(2);
         %   end
         %   prob = LOC.algs(iAlg).algJ(iTau).get_current_probability();
         %   LOC.algs(iAlg).eventprob(idx,:,iTau) = prob;
         %   % Store the important probability and event code information
         %   try % Set the less important information separately
         %     resid = LOC.algs(iAlg).algJ(iTau).get_current_residuals();
         %     contrib = LOC.algs(iAlg).algJ(iTau).get_contributing_signals();
         %     msg = LOC.algs(iAlg).algJ(iTau).get_message();
         %     if ~isempty(resid),
         %       LOC.algs(iAlg).residuals(idx,:,iTau) = resid';
         %     end;
         %     if ~isempty(contrib)
         %       LOC.algs(iAlg).event_contrib(idx,:,iTau) = int8(contrib);
         %     end
         %     if ~isempty(msg)
         %       LOC.algs(iAlg).comments{idx,1,1} = char(msg);
         %     end
         %   catch ERR
         %     cws.errTrace(ERR);
         %   end
         % end
         */
        executing = true;
        Step step = this.getCurrentStep();
        Message retMessage;
        HashMap<String, Object> statusData = new HashMap();
        statusData.put("code", 0);
        statusData.put("message", "Success");
        statusData.put("msgsConsumed", 0);
        statusData.put("msgsProduced", 0);
        HashMap resultData = new HashMap();
        Message newData;
        Message resultMsg;
        try {

            // read in new data from router
            int count = 0;
            newData = this.pollMessageFromInbox(step);
            //while (!all(this.recvdStatusForCurrentStep) || newData != null) {
            Double value;
            String chTag;
            while (newData != null) {
                //process data
                chTag = newData.getTag();
                value = (Double) newData.getData().get("value");
                DataChannel channel = this.channels.get(chTag);
                count++;
                if (channel != null) {
                    channel.addNewValue(value, step);
                }
                newData = this.pollMessageFromInbox(step);
            }
            statusData.put("msgsConsumed", count);
            resultData.put("message", "" + count + " messages read in");
            /*
             * evaluateModel event detection algorithms
             */
            int idx = step.getIndex();
            HashMap workflowRes = workflow.evaluateWorkflow(idx);
            EventStatus curStatus = workflow.getStatus();
            if (this.lastStatus != EventStatus.POSSIBLE_EVENT
                    && curStatus == EventStatus.POSSIBLE_EVENT) {
                this.events.add(currentEvent);
            }
            // TODO: Event Identifier Event Handling
            if (this.currentEvent.isCompleted()) {
                LOG.info(this.currentEvent.summarize());
                LOG.debug(this.currentEvent);
                this.currentEvent = new EventRecord(this.name, channelList);
                this.currentEvent.setWorkflowChannels(workflow.getChannels());
            }
            lastStatus = curStatus;
            if (curStatus != EventStatus.UNINITIALIZED) {
                currentEvent.removeFirstElement(
                        workflow.getPreEventHistoryCount());
                currentEvent.addStep(step);
                currentEvent.addProbabilityAndStatus(workflow.getProbability(),
                        workflow.getStatus());
                currentEvent.setChannelLimitViolations(
                        workflow.getChannelViolations());
                currentEvent.setChannelContributed(
                        workflow.getChannelContributed());
                currentEvent.setChannelRawData(workflow.getChannelRawData());
                currentEvent.setChannelResiduals(workflow.getChannelResiduals());
                currentEvent.setChannelStatuses();
            }
            if (curStatus != EventStatus.NORMAL
                    && curStatus != EventStatus.OUTLIER_DETECTED
                    && curStatus != EventStatus.UNINITIALIZED
                    && curStatus != EventStatus.STATION_CALIBRATING) {
                LOG.warn(
                        "Station " + this.name + " " + curStatus.toString() + " at " + step.toString());
            } else {
                LOG.trace(
                        "Station " + this.name + " " + curStatus.toString() + " at " + step.toString());
            }
            String[] parameters = workflow.getChannelParameters();
            Short[] contrib = workflow.getChannelContributed();
            String contribString = "";
            for (int i = 0; i < contrib.length; i++) {
                if (contrib[i] > 0) {
                    contribString += parameters[i] + " ";
                }
            }
            resultData.put("eventCode", workflow.getStatus());
            resultData.put("eventProbability", workflow.getProbability());
            resultData.put("contribParameters", contribString);
            resultData.put("workflowName", workflow.getName());
            resultData.put("byChannelResiduals", new ArrayList(
                    Arrays.asList(workflow.getChannelResiduals())));
            resultData.put("byChannelContribToEvents", new ArrayList(
                    Arrays.asList(workflow.getChannelContributed())));
            resultData.put("byChannelParameters", new ArrayList(
                    Arrays.asList(workflow.getChannelParameters())));
            resultData.put("byChannelTags", new ArrayList(
                    Arrays.asList(workflow.getChannelTags())));
            resultMsg = new Message(MessageType.RESULT, this.tag, resultData,
                    step);
            this.pushMessageToOutbox(resultMsg);
            statusData.put("msgsProduced", 1);
            //

        } catch (Exception ex) {
            executing = false;
            LOG.fatal("error executing event detection on Station " + this.name,
                    ex);
            statusData.put("message", ex.getMessage());
            statusData.put("status", 1);
            statusData.put("exception", ex);
        }
        executing = false;
        retMessage = new Message(MessageType.CONTROL,
                this.name + "_STATUS", statusData, step);
        return 0;
        //statusData.toString();
    }

    @Override
    public Descriptor getConfiguration() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public boolean[] getRecvdStatusForCurrentStep() {
        return recvdStatusForCurrentStep;
    }

    public void setRecvdStatusForCurrentStep(boolean[] recvdStatusForCurrentStep) {
        this.recvdStatusForCurrentStep = recvdStatusForCurrentStep;
    }

    public ArrayList<String> getSynchronizeToTags() {
        return synchronizeToTags;
    }

    public void setSynchronizeToTags(ArrayList<String> synchronizeToTags) {
        this.synchronizeToTags = synchronizeToTags;
    }

    /**
     * Read messages from inbox and check for synchronized stepping and mark
     * status messages if that exists. If the step is null AND synchronized
     * stepping is set to true, then this will produce null. Otherwise, this
     * will return the next matching message (of equal or earlier step value.
     *
     * @param step the data step up to which to look for messages
     * @return the message, if one exists
     */
    @Override
    public Message pollMessageFromInbox(Step step) {
        if (step == null) {
            // if stepsAreSynchronized, we can't read messages from the inbox
            // without a step to process -- return null in that case
            return null;
        }
        Message temp = this.inbox.peek();
        if (temp == null) {
            return null;
        }
        if (temp.getStep().compareTo(step) < 1) {
            this.inbox.remove(temp);
            if (this.synchronizeToTags.contains(temp.getTag())) {
                if (temp.getStep().compareTo(step) == 0) {
                    // LOG.debug("sync tag ''" + temp.name + "'' for step ''" + step + "'' found");
                    int idx = this.synchronizeToTags.indexOf(temp.getTag());
                    this.recvdStatusForCurrentStep[idx] = true;
                }
            }
            return temp;
        }
        return null;
    }

    @Override
    public String[] parseStatusCode(int code) {
        if (code == 0) {
            return new String[]{"0", "SUCCESS"};
        } else {
            return EventStatus.parseCode(code);
        }
    }

}
