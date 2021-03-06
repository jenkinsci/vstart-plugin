/* 
 * Copyright (C) 2015 VisionSpace Technologies, Lda.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.visionspace.jenkins.vstart.plugin;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author pedro.marinho
 */
public class VSPluginPublisher extends Publisher {

    @DataBoundConstructor
    public VSPluginPublisher() {

    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return new VSPluginProjectAction(project);
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, final BuildListener listener) {

        Result buildResult = build.getResult();

        if (!Result.SUCCESS.equals(buildResult)) {
            // Don't process for unsuccessful builds
            listener.getLogger().println("Build status is not SUCCESS (" + build.getResult().toString() + ").");
            return false;
        }
        
        VSPluginPerformer performer = new VSPluginPerformer();
        //add build action
        performer.addBuildAction(build);

        try {

            //DO REPORT HERE
            FilePath jPath = new FilePath(build.getWorkspace(), build.getWorkspace() + "/VSTART_JSON");

            if (!jPath.exists()) {
                return false;
            }

            String filePath = jPath + "/VSTART_JSON_"
                    + build.getId() + ".json";
            
            //read .json file
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            JSONArray reports = new JSONArray(content);
            
            //Generate html report
            VSPluginHtmlWriter htmlWriter = new VSPluginHtmlWriter();
            boolean reportResult = htmlWriter.doHtmlReport(build, reports);
            
            return reportResult;
            
        } catch (IOException ex) {
            Logger.getLogger(VSPluginPublisher.class.getName()).log(Level.SEVERE, null, ex);
            listener.getLogger().println("Exception during the Publisher's perform! -> " + ex.getLocalizedMessage());
            return false;
        } catch (InterruptedException ex) {
            Logger.getLogger(VSPluginPublisher.class.getName()).log(Level.SEVERE, null, ex);
            listener.getLogger().println("Exception during the VSTART run! -> " + ex.getLocalizedMessage());
            return false;
        }
    }

    @Override
    public BuildStepDescriptor getDescriptor() {
        return (BuildStepDescriptor) super.getDescriptor();
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static class DescriptorRecorder extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return FreeStyleProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "Publish VSTART report.";
        }

    }
}
