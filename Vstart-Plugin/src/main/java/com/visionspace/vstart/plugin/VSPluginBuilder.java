package com.visionspace.vstart.plugin;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import static com.google.common.collect.Lists.newArrayList;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import com.visionspace.vstart.api.Vstart;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;

/**
 * Vstart Plugin Builder
 *
 * @author pedro.marinho
 */
public class VSPluginBuilder extends Builder {

    //private final String vstAddress;
    //private final String credentialsId;
    private final long vstProjectId;
    private final long testCase;

    /**
     * VSPluginBuilder Constructor
     *
     * @param vstAddress
     * @throws URISyntaxException
     */
    @DataBoundConstructor
    public VSPluginBuilder(long vstProjectId, long testCase) {
        //this.vstAddress = getDescriptor().getVstAddress();
        //this.credentialsId = getDescriptor().getCredentialsId();
        this.vstProjectId = vstProjectId;
        this.testCase = testCase;
    }

    /*public String getVstAddress() {
     return vstAddress;
     }*/
    public long getVstProjectId() {
        return vstProjectId;
    }

    /*public String getCredentialsId() {
     return credentialsId;
     }*/
    public long getTestCase() {
        return testCase;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        try {
            //get user and password
            StandardUsernamePasswordCredentials cred = CredentialsProvider.findCredentialById(getDescriptor().getCredentialsId(), StandardUsernamePasswordCredentials.class, build);
            String user = cred.getUsername();
            String pass = cred.getPassword().getPlainText();
            
            //Instanciation of VSTART API object
            Vstart vst = new Vstart(getDescriptor().getVstAddress(), user, pass);
            VSPluginPerformer performer = new VSPluginPerformer(vst);
            
            //Informing the start of the perform method
            listener.getLogger().println("\nA VSTART buildstep has started.");
            
            //Informing connection
            listener.getLogger().println("\nTrying to connect to the VSTART server...");
            
            //Login
            performer.getVstObject().login(user, pass);
            
            //Informing success on connection
            listener.getLogger().println("Connection established with the VSTART server.");
            
            //add action
            performer.addBuildAction(build);
            
            //test case validation
            boolean test = performer.validateTestCase(testCase, build, listener);

            //In case of validation failure -> Build cannot 
            if (!test) {
                performer.getVstObject().close();
                return false;
            }
            
            //Run VSTART
            int timeInterval = 2000; //wait 2 seconds to ask for a "finished" test case status 
            Long reportId = performer.runVstart(testCase, listener, timeInterval);
            
            //run status validation
            if(reportId == 0l){
                performer.getVstObject().close();
                return false;
            }
            
            //log JSON file to workspace
            boolean result = performer.logToWorkspace(reportId, build);
            
            //close VSTART session
            performer.getVstObject().close();
            
            //Logging to console
            listener.getLogger().println("VSTART buildstep has ended with success.");
            
            return result;
            
        } catch (URISyntaxException ex) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return false;
    }



    // Overridden for better type safety.
// If your plugin doesn't really define any property on Descriptor,
// you don't have to do this.
@Override
        public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    

}

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
public static class Descriptor extends BuildStepDescriptor<Builder> {

    public int incSeconds = 0;

    private String vstAddress;
    private String vstUser;
    private String vstPass;
    private String credentialsId;
    private Vstart vst;
    private int randomId;

    public Descriptor() {
        load();
        //TODO: make vst transient
        vst = null;
        //end.
    }

    @Override
    public String getDisplayName() {
        return "Execute VSTART tasks.";
    }

    public String getVstAddress() {
        return this.vstAddress;
    }

    public String getVstUser() {
        return this.vstUser;
    }

    public String getVstPass() {
        return this.vstPass;
    }

    public String getCredentialsId() {
        return this.credentialsId;
    }

    public Vstart getVst() {
        return vst;
    }

    public void setVst(Vstart vst) {
        this.vst = vst;
    }

    public void setVstAddress(String s) {
        this.vstAddress = s;
    }

    public void setVstUser(String user) {
        this.vstUser = user;
    }

    public void setVstPass(String pass) {
        this.vstPass = pass;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public FormValidation doCheckLogin(@QueryParameter("vstAddress") final String address, @QueryParameter("credentialsId") final String credentials) {

        String user = new String();
        String pass = new String();
        List<DomainRequirement> domainRequirements = newArrayList();
        List<StandardUsernamePasswordCredentials> c = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), null, domainRequirements);

        for (int i = 0; i < c.size(); i++) {
            if (c.get(i).getId().equals(credentials)) {
                user = c.get(i).getUsername();
                pass = c.get(i).getPassword().getPlainText();
                break;
            }
        }

        Vstart vstObject;
        try {
            vstObject = new Vstart(address, user, pass);
            vstObject.login(user, pass);
            vstObject.close();
            return FormValidation.ok("Login: OK!");
        } catch (URISyntaxException ex) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, ex);
            return FormValidation.error("Login: error!");
        } catch (IOException e) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, e);
            return FormValidation.error("Login: error!");
        }
    }

    public synchronized JSONArray getProjects() {
        try {
            if (vst == null) {
                vst = new Vstart(vstAddress, vstUser, vstPass);
            }
            vst.login(vstUser, vstPass);
            JSONArray jArr = vst.listUserProjects();
            vst.close();
            return jArr;
        } catch (IOException e) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, e);
            return null;
        } catch (URISyntaxException ex) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public boolean isValidProjectId(long id) {

        JSONArray jArr = getProjects();

        for (int i = 0; i < jArr.length(); i++) {
            long pId = jArr.getJSONObject(i).getLong("id");
            if (pId == id) {
                return true;
            }
        }

        return false;
    }

    public FormValidation doCheckVstProjectId(@QueryParameter("vstProjectId") final long id) {
        if (isValidProjectId(id)) {
            return FormValidation.ok("Available project.");
        } else {
            return FormValidation.error("This project does not exist or it is not available at the current time.");
        }
    }

    public synchronized boolean isValidTestCase(long id) {
        try {
            if (vst == null) {
                vst = new Vstart(vstAddress, vstUser, vstPass);
            }
            vst.login(vstUser, vstPass);
            boolean test = vst.canRun(id);
            vst.close();
            return test;
        } catch (IOException e) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, e);
            return false;
        } catch (URISyntaxException ex) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    public FormValidation doCheckTestCase(@QueryParameter("testCase") final long id) {
        boolean test = isValidTestCase(id);
        if (test) {
            return FormValidation.ok("Test Case Available.");
        } else {
            return FormValidation.error("This test case is not available at the time, please select another.");
        }
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        // Indicates that this builder can be used with all kinds of project types
        return FreeStyleProject.class.isAssignableFrom(jobType);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
        // set that to properties and call save().
        req.bindJSON(this, formData);
        List<DomainRequirement> domainRequirements = newArrayList();
        List<StandardUsernamePasswordCredentials> c = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), null, domainRequirements);
            //StandardUsernamePasswordCredentials cred = CredentialsProvider.findCredentialById(formData.getString("credentialsId"), StandardUsernamePasswordCredentials.class, this);
        //setVstUser(cred.getUsername());
        //setVstPass(cred.getPassword().getPlainText());
        // ^Can also use req.bindJSON(this, formData);
        //  (easier when there are many fields; need set* methods for this, like setUseFrench)
        for (int i = 0; i < c.size(); i++) {
            if (c.get(i).getId().equals(formData.getString("credentialsId"))) {
                setVstUser(c.get(i).getUsername());
                setVstPass(c.get(i).getPassword().getPlainText());
                break;
            }
        }
        vst = null;
        save();
        return super.configure(req, formData);
    }

    @Override
    public VSPluginBuilder newInstance(final StaplerRequest req, final JSONObject formData) throws FormException {
        return req.bindJSON(VSPluginBuilder.class, formData);
    }

    public ListBoxModel doFillVstProjectIdItems() throws IOException {

        JSONArray array = getProjects();
        //validation
        if (array.isNull(0)) {
            return new ListBoxModel();
        }
        ListBoxModel items = new ListBoxModel();

        for (int j = 0; j < array.length(); j++) {
            String project = array.getJSONObject(j).getString("name");
            String id = Long.toString(array.getJSONObject(j).getLong("id"));
            items.add(new ListBoxModel.Option(project, id, false));
        }

        return items;

    }

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?, ?> owner) {
        if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) { // or whatever permission is appropriate for this page
            // Important! Otherwise you expose credentials metadata to random web requests.
            return new ListBoxModel();
        }

        List<DomainRequirement> domainRequirements = newArrayList();

        return new StandardUsernameListBoxModel().withEmptySelection().withAll(
                CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, owner, null, domainRequirements));
    }

    public synchronized JSONArray getTestCases(long vstProjectId) {

        try {
            if (vst == null) {
                vst.close();
                vst = new Vstart(vstAddress, vstUser, vstPass);
            }
            vst.login(vstUser, vstPass);
            JSONArray array = vst.listProjectTestCases(vstProjectId);
            vst.close();
            return array;
        } catch (IOException e) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, e);
            return null;
        } catch (URISyntaxException ex) {
            Logger.getLogger(VSPluginBuilder.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public ListBoxModel doFillTestCaseItems(@QueryParameter("vstProjectId") final long vstProjectId) {
        JSONArray array = getTestCases(vstProjectId);
        //validation
        if (array == null) {
            return new ListBoxModel();
        }
        ListBoxModel items = new ListBoxModel();

        for (int j = 0; j < array.length(); j++) {
            String testcase = array.getJSONObject(j).getString("name");
            String id = Long.toString(array.getJSONObject(j).getLong("id"));
            if (id.equals(Long.toString(vstProjectId))) {
                items.add(new ListBoxModel.Option(testcase, id, true));
            } else {
                items.add(new ListBoxModel.Option(testcase, id, false));
            }
        }
        return items;
    }
}
}