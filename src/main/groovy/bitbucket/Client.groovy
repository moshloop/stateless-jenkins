package bitbucket

import org.apache.http.client.methods.*
import org.apache.http.impl.client.*
import org.apache.http.client.*
import org.apache.http.entity.*
import groovy.json.JsonSlurper

class Client {
    String host, user, pass;

    public Repository repo(String project, String repo) {
        return new Repository(api: this, project: project, repo: repo)
    }

    public String getAuth() {
        return "Basic " + "${user}:${pass}".bytes.encodeBase64().toString()
    }

    def get(String url) {
        String uri = "${host}/${url}"
        def response = new DefaultHttpClient().execute(
            RequestBuilder
                .get(uri)
                .addHeader("Authorization", auth)
                .build()
            )
        if (response.statusLine.statusCode > 299) {
            throw new Exception(response.statusLine.statusCode + ": " + response.entity.content.text)
        }
        return new JsonSlurper().parseText(response.entity.content.text)
    }

    def post(String url, String body) {
        String uri = "${host}/${url}"
        def response = new DefaultHttpClient().execute(
            RequestBuilder
                .post(uri)
                .addHeader("Authorization", auth)
                .addHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(body))
                .build()
            )
        if (response.statusLine.statusCode > 299) {
            throw new Exception(response.statusLine.statusCode + ": " + response.entity.content.text)
        }
        return new JsonSlurper().parseText(response.entity.content.text)
    }
}

class Repository {
    Client api
    String project, repo

    String getUrl() {
        return "rest/api/1.0/projects/${project}/repos/${repo}"
    }

    def getPullRequests() {
        return api.get("${url}/pull-requests").values
    }

    PullRequest getPullRequest(String id) {
        return new PullRequest(repo: this, id: id)
    }

    def findPullRequestByRef(String ref) {
        def pr = getPullRequests().find({it.fromRef.latestCommit == ref})
        if (pr != null) {
            return new PullRequest(
                repo: this,
                id: pr.id,
                title: pr.title,
                link: pr.links.self.href[0]
                )
        }
    }
}
class PullRequest {
    Repository repo
    String id, title, link
    def changedFiles

    String getUrl() {
        return "${repo.url}/pull-requests/${id}"
    }

    def getDetails() {
        return repo.api.get(url)
    }

    def getComments() {
          return repo.api.get(url + "/activities?fromType=COMMENT").values.findAll({it.action == 'COMMENTED'})
    }

    def comment(String text) {
        text = text.replace("\n", "\\n")
        return repo.api.post(url + "/comments", '{"text": "' + text + '"}')
    }

    def approve() {
        return repo.api.post(url + "/approve", "{}")
    }

    def decline() {
        return repo.api.post(url + "/decline?version=${details().version}", "{}")
    }

    def reopen() {
       return repo.api.post(url + "/reopen?version=${details().version}", "{}")
    }
}

