# jenkins-shared-libs
Some shared libs for jenkins for testing purposes

This is going to build https://github.com/jmarrec/TestCpp-GHA-Coverage as an example

See the `run.sh` on [jmarrec/Jenkins-Test](https://github.com/jmarrec/Jenkins-Test/blob/master/run.sh)

## Notes

**Everything is run on my host machine. I run jenkins in a docker, and my host machine is a node for building the C++.**

```bash
cd ../Jenkins-Test/jenkins-data
mkdir jenkins-data
# Launch the docker
./run.sh
```

Create ssh key pairs to communicate between from docker to host (and potentially for github too, though you'll see later we'll use a Github App actually...)

```
docker exec -it jenkins-test /bin/bash
cd /var/jenkins_home
whoami
=> root
cat /etc/passwd
[...]
jenkins:x:1000:1000:Linux User,,,:/var/jenkins_home:/bin/bash

bash-5.1# su -s /bin/bash jenkins
bash-5.1$ whoami
jenkins
bash-5.1$ ssh-keygen
```

I let id_rsa, put no password.



On the **host**:

```bash
ifconfig => docker0 is at 172.17.0.1

sudo ssh-keyscan -H 172.17.0.1 > known_hosts

sudo apt install openssh-server
cat jenkins-data/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys

cd /home/julien/Software/Others/Jenkins-Test
mkdir agent
```

Now add a new computer (http://localhost:8080/computer/)

* name: desktop-ubuntu-1804
* number of executors: 1
* remote root directory: /home/julien/Software/Others/Jenkins-Test/agent/  (this is where this repo is)
* Use this node as much as possible
* Launch method: Launch agents via SSH
* Host: 172.17.0.1
* Credentials: a SSH one, with the content of the private key created above at `/home/julien/Software/Others/Jenkins-Test/jenkins-data/.ssh/jenkins_test`

I created a shared lib at: https://github.com/jmarrec/jenkins-shared-libs (this repo)

Dashboard > Manage Jenkins > Configure System > Global Pipeline Libraries

* name: jenkins_shared_libs
* default version: main
* Allow default version to be overriden
* Git
* https://github.com/jmarrec/jenkins-shared-libs
* Credentials: <strike>SSH_master_julien_desktop</strike> **Edit**: the Github App credentials!



Manage Plugin > install:

* Pipeline GitHub Notify Step (githubNotify)
* xUnit
* Cobertura
* GitHub Pull Request Coverage Status (addon for Cobertura)
* **Edit:** + GitHub Checks plugin

I needed to tweak the PATH env variable on the agent, so it picks up my host conan in particular (which is from a virtualenv)

Apparently the publishCoverage doesn't work with github SSH, you need to configure with **Github App**

### Github App

You need to have a webhook to post events to. But obviously Github won't let you use localhost:8080.

We'll use ngrok: https://ngrok.com/ and signup for an account then install it.

```bash
wget https://bin.equinox.io/c/4VmDzA7iaHb/ngrok-stable-linux-amd64.zip
unzip ngrok
sudo mv ngrok /usr/local/bin
ngrok authtoken <ENTER_TOKEN_HERE>

ngrok http 8080
```

You can now use the endpoint ngrok prompts to the console for the webhook: `https://<ID>.ngrok.io`

For the webhook url, you'll put: `https://e050-89-248-77-60.ngrok.io/github-webhook/`

See https://www.jenkins.io/blog/2020/04/16/github-app-authentication/ for more info on how to setup the rest of the parameters (links to https://github.com/jenkinsci/github-branch-source-plugin/blob/master/docs/github-app.adoc)


Note about GH App permissions: as of today 2021-09-01, when setting it the gh app on jenkins multipipeline config job, it was throwing because it couldn't parse some event types.
I had to disable a few (PR Review Comment, PR review thread, Workflow Job, and Merge queue entry)

```
2021-09-01 13:02:41.159+0000 [id=125]	WARNING	o.j.p.g.Connector#checkScanCredentials: Exception validating credentials Github App for Jenkins Test
com.fasterxml.jackson.databind.exc.InvalidFormatException: Cannot deserialize value of type `org.kohsuke.github.GHEvent` from String "merge_queue_entry": not one of the values accepted for Enum class: [GOLLUM, ALL, INTEGRATION_INSTALLATION_REPOSITORIES, META, DOWNLOAD, FORK_APPLY, CHECK_RUN, MARKETPLACE_PURCHASE, PROJECT_CARD, PUSH, PAGE_BUILD, TEAM_ADD, REPOSITORY_VULNERABILITY_ALERT, PACKAGE, DELETE, DEPLOYMENT_STATUS, WATCH, FOLLOW, ORG_BLOCK, ORGANIZATION, COMMIT_COMMENT, ISSUE_COMMENT, REPOSITORY_IMPORT, GITHUB_APP_AUTHORIZATION, CODE_SCANNING_ALERT, CHECK_SUITE, FORK, GIST, CONTENT_REFERENCE, REPOSITORY, INSTALLATION_REPOSITORIES, REPOSITORY_DISPATCH, MILESTONE, STAR, LABEL, MEMBERSHIP, SECURITY_ADVISORY, PROJECT_COLUMN, TEAM, PULL_REQUEST_REVIEW, RELEASE, PUBLIC, WORKFLOW_RUN, STATUS, PULL_REQUEST, PROJECT, WORKFLOW_DISPATCH, CREATE, MEMBER, REGISTRY_PACKAGE, INSTALLATION, DEPLOYMENT, PULL_REQUEST_REVIEW_COMMENT, DEPLOY_KEY, PING, ISSUES]
 at [Source: (String)"{"id":135594,"slug":"jenkins-testcpp-gha-coverage","node_id":"A_kwDOAFOal84AAhGq","owner":{"login":"jmarrec","id":5479063,"node_id":"MDQ6VXNlcjU0NzkwNjM=","avatar_url":"https://avatars.githubusercontent.com/u/5479063?v=4","gravatar_id":"","url":"https://api.github.com/users/jmarrec","html_url":"https://github.com/jmarrec","followers_url":"https://api.github.com/users/jmarrec/followers","following_url":"https://api.github.com/users/jmarrec/following{/other_user}","gists_url":"https://api.github.c"[truncated 1194 chars]; line: 1, column: 1407] (through reference chain: org.kohsuke.github.GHApp["events"]->java.util.ArrayList[6])

```


**NOTE: need to also add GitHub Checks plugin**

Obviously, you need to enable on github for your app the "Checks": read & write permission


**On your repo settings, you then need to accept the permission update!**
