package org.sakaiproject.ddo.tool.pages;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigator;
import org.apache.wicket.markup.repeater.DefaultItemReuseStrategy;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.util.resource.AbstractResourceStreamWriter;
import org.sakaiproject.ddo.logic.SakaiProxy;
import org.sakaiproject.ddo.model.Feedback;
import org.sakaiproject.ddo.model.Submission;
import org.sakaiproject.ddo.model.SubmissionFile;

public class ArchivePage extends BasePage {

    ArchiveDataProvider archivedProvider;

    public ArchivePage() {
        disableLink(archivePageLink);

        // Add a link to refresh the tables on the page
        Link<Void> refreshPage = new Link<Void>("refreshPage") {
            public void onClick() {
                setResponsePage(new ArchivePage());
            }
        };
        add(refreshPage);

        add(new Label("restore-header", "Restore").setVisible(sakaiProxy.isDDOAdmin()));

        //get list of items from db, wrapped in a dataprovider
        archivedProvider = new ArchiveDataProvider();

        //present the reviewed data in a table
        final DataView<Submission> dataViewArchived = new DataView<Submission>("archived", archivedProvider) {

            @Override
            public void populateItem(final Item item) {

                DateFormat df = new SimpleDateFormat("MMM d, yyyy h:mm a");

                final Submission submission = (Submission) item.getModelObject();
                final String submissionStatus = submission.getStatus();
                item.add(new Label("submittedBy", sakaiProxy.getUserDisplayName(submission.getSubmittedBy())));
                item.add(new Label("username", sakaiProxy.getUserDisplayId(submission.getSubmittedBy())));
                item.add(new Label("submissiondate", df.format(submission.getSubmissionDate())));
                item.add(new Label("status", submissionStatus));
                Link<Void> feedback;
                Label feedbackLabel;
                Link<Void> editFeedback;
                Label editFeedbackLabel;
                final List<Feedback> feedbackList = projectLogic.getFeedbackForSubmission(submission.getSubmissionId());
                if (feedbackList != null && !feedbackList.isEmpty()) {
                    Feedback f = feedbackList.get(0);
                    final long feedbackId = f.getFeedbackId();
                    item.add(new Label("reviewedBy", sakaiProxy.getUserDisplayName(f.getReviewedBy())));
                    item.add(new Label("reviewDate", df.format(f.getReviewDate())));
                    feedback = new Link<Void>("feedback") {
                        @Override
                        public void onClick() {
                            setResponsePage(new FeedbackPage(feedbackId,"staff"));
                        }
                    };
                    editFeedback = new Link<Void>("editFeedback") {
                        @Override
                        public void onClick() {
                            setResponsePage(new EditFeedback(feedbackId));
                        }
                    };
                    feedbackLabel = new Label("feedbackLabel","View");
                    editFeedbackLabel = new Label ("editFeedbackLabel", "Edit");
                } else {
                    item.add(new Label("reviewedBy",""));
                    item.add(new Label("reviewDate",""));
                    feedback = new Link<Void>("feedback"){
                        @Override
                        public void onClick() {}
                    };
                    editFeedback = new Link<Void>("editFeedback"){
                        @Override
                        public void onClick() {}
                    };
                    feedbackLabel = new Label("feedbackLabel","");
                    editFeedbackLabel = new Label ("editFeedbackLabel", "");
                }
                feedback.add(feedbackLabel);
                item.add(feedback);
                editFeedback.add(editFeedbackLabel);
                item.add(editFeedback);
                Link<Void> streamDownloadLink = new Link<Void>("document") {

                    @Override
                    public void onClick() {

                        AbstractResourceStreamWriter rstream = new AbstractResourceStreamWriter() {

                            @Override
                            public void write(OutputStream output) throws IOException {
                                output.write(sakaiProxy.getResource(submission.getDocumentRef()).getBytes());
                            }
                        };

                        ResourceStreamRequestHandler handler = new ResourceStreamRequestHandler(rstream, sakaiProxy.getResource(submission.getDocumentRef()).getFileName());
                        getRequestCycle().scheduleRequestHandlerAfterCurrent(handler);
                    }
                };

                item.add(streamDownloadLink);
                SubmissionFile sf = sakaiProxy.getResource(submission.getDocumentRef());
                streamDownloadLink.add(new Label("fileName", sf==null?"Cannot find file.":sf.getFileName()));
                item.add(new ContextImage("submissionIcon", new Model<String>(sakaiProxy.getResourceIconUrl(submission.getDocumentRef()))));
                item.add(new Label("fileSize", sakaiProxy.getResourceFileSize(submission.getDocumentRef())));
                // Restore link
                Link<Void> restoreLink = new Link<Void>("restore-link") {
                    @Override
                    public void onClick() {
                        submission.setStatus(Submission.STATUS_REVIEWED);
                        if(projectLogic.updateSubmissionStatus(submission)){
                            getSession().info(getString("success.restored_submission"));
                            setResponsePage(new ArchivePage());
                        } else {
                            error(getString("error.restored_submission"));
                        }
                    }
                };
                item.add(restoreLink);
                restoreLink.setVisible(sakaiProxy.isDDOAdmin());
            }
        };
        dataViewArchived.setItemReuseStrategy(new DefaultItemReuseStrategy());
        dataViewArchived.setItemsPerPage(7);
        add(dataViewArchived);

        add(new Label("numberOfReviewed",archivedProvider.size()));

        //add a pager to our table, only visible if we have more than 5 items
        add(new PagingNavigator("archivedNavigator", dataViewArchived) {

            @Override
            public boolean isVisible() {
                if(archivedProvider.size() > 7) {
                    return true;
                }
                return false;
            }

            @Override
            public void onBeforeRender() {
                super.onBeforeRender();

                //clear the feedback panel messages
                clearFeedback(feedbackPanel);
            }
        });
    }

    /**
     * DataProvider to manage our review list
     *
     */
    private class ArchiveDataProvider implements IDataProvider<Submission> {

        private List<Submission> list;

        private List<Submission> getData() {
            if(list == null) {
                list = projectLogic.getAllArchivedSubmissions();
                Collections.sort(list, new Comparator<Submission>() {
                    @Override
                    public int compare(Submission s1, Submission s2) {
                        long t1 = s1.getSubmissionDate().getTime();
                        long t2 = s2.getSubmissionDate().getTime();
                        if (t1 < t2) return -1;
                        else if (t1 > t2) return 1;
                        else return 0;
                    }
                });
                Collections.reverse(list);
            }
            return list;
        }


        @Override
        public Iterator<Submission> iterator(long first, long count){
            int f = (int) first; //not ideal but ok for demo
            int c = (int) count; //not ideal but ok for demo
            return getData().subList(f, f + c).iterator();
        }

        @Override
        public long size(){
            return getData().size();
        }

        @Override
        public IModel<Submission> model(Submission object){
            return new DetachableSubmissionModel(object);
        }

        @Override
        public void detach(){
            list = null;
        }
    }

    /**
     * Detachable model to wrap a Submission
     *
     */
    private class DetachableSubmissionModel extends LoadableDetachableModel<Submission> {

        private final long id;

        /**
         * @param s
         */
        public DetachableSubmissionModel(Submission s){
            this.id = s.getSubmissionId();
        }

        /**
         * @param id
         */
        public DetachableSubmissionModel(long id){
            this.id = id;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        public int hashCode() {
            return Long.valueOf(id).hashCode();
        }

        /**
         * used for dataview with ReuseIfModelsEqualStrategy item reuse strategy
         *
         * @see org.apache.wicket.markup.repeater.ReuseIfModelsEqualStrategy
         * @see java.lang.Object#equals(java.lang.Object)
         */
        public boolean equals(final Object obj){
            if (obj == this){
                return true;
            }
            else if (obj == null){
                return false;
            }
            else if (obj instanceof DetachableSubmissionModel) {
                DetachableSubmissionModel other = (DetachableSubmissionModel)obj;
                return other.id == id;
            }
            return false;
        }

        /**
         * @see org.apache.wicket.model.LoadableDetachableModel#load()
         */
        protected Submission load(){

            // get the submission
            return projectLogic.getSubmission(id);
        }
    }
}
