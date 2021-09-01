

def call() {

  node("desktop-ubuntu-1804") {
    String base_dir = "/home/julien/Software/Others/Jenkins-Test/agent/TestCpp-GHA-Coverage";
    String build_dir = "${base_dir}/build-coverage";

    stage("Checkout") {

      checkout([
        $class: 'GitSCM',
        branches:  [[name: "FETCH_HEAD"]],
        userRemoteConfigs: [
          [
            credentialsId: 'SSH_master_julien_desktop',
            name: 'origin',
            refspec: "+refs/pull/${env.CHANGE_ID}/head:refs/remotes/origin/PR-${env.CHANGE_ID}",
            url: 'git@github.com:jmarrec/TestCpp-GHA-Coverage.git']
          ]
      ])

      if (fileExists(build_dir) == "false") {
        sh("mkdir ${build_dir}") ;
      }
    } // end stage("checkout")


  } // end node desktop-ubuntu-1804
} // end call()
