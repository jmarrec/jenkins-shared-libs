

def call() {

  def buildResult = "PENDING";
  String description = "Incremental ubuntu-20.04 Build";
  String context = 'ubuntu-20.04-incremental';
  String githubToken = 'github-app-jmarrec'; // We use the Github App and not some username/password as token, so that Checks API is enabled

  node("desktop-ubuntu-1804") {

    sh("python --version");
    sh("conan --version");


    String base_dir = "/home/julien/Software/Others/Jenkins-Test/agent/TestCpp-GHA-Coverage";
    String build_dir = "${base_dir}/build-coverage";

    //githubNotify(
      //description: "${description}",
      //context: "${context}",
      //status: "${buildResult}",
      //credentialsId: "${githubToken}"
    //);

    stage("Checkout") {
      dir(base_dir) {

        checkout scm;
        //if (env.CHANGE_ID) {
          //checkout([
            //$class: 'GitSCM',
            //branches:  [[name: "FETCH_HEAD"]],
            //userRemoteConfigs: [
              //[
                //credentialsId: "${githubToken}",
                //name: 'origin',
                //refspec: "+refs/pull/${env.CHANGE_ID}/head:refs/remotes/origin/PR-${env.CHANGE_ID}",
                //url: 'git@github.com:jmarrec/TestCpp-GHA-Coverage.git'
              //]
            //],
            //extensions: [[$class: 'GitSCMChecksExtension', verboseConsoleLog: true]]
          //]);
        //} else {
          //checkout([
            //$class: 'GitSCM',
            //branches:  [[name: "*/${env.BRANCH_NAME}"]],
            //userRemoteConfigs: [
              //[
                //credentialsId: "${githubToken}",
                //name: 'origin',
                //url: 'git@github.com:jmarrec/TestCpp-GHA-Coverage.git'
              //]
            //],
            //extensions: [[$class: 'GitSCMChecksExtension', verboseConsoleLog: true]]
          //]);

        //}

        if (fileExists(build_dir) == "false") {
          sh("mkdir ${build_dir}") ;
        }
      } // end dir(base_dir)
    } // end stage("checkout")

    stage("CMake Configure") {
      dir (build_dir) {
        try {
          sh("cmake -G Ninja -DCMAKE_BUILD_TYPE:STRING=Debug -DCMAKE_EXPORT_COMPILE_COMMANDS:BOOL=ON -DENABLE_COVERAGE:BOOL=ON -DCMAKE_EXPORT_COMPILE_COMMANDS:BOOL=ON ${base_dir}/");
        } catch (Exception e) {
          e.printStackTrace();
          buildResult = "FAILURE";
          githubNotify(description: "${description} - CMake failed",  context: "${context}", status: "${buildResult}" , credentialsId: "${githubToken}");
          error("cmake configure step failed. check logs");
        }
      }
    }

    stage("Build") {
      dir(build_dir) {
        publishChecks(conclusion: 'NONE', name: 'ubuntu-20.04-incremental publishChecks', status: 'IN_PROGRESS', title: 'Build');

        try {
          sh("ninja");
          publishChecks(conclusion: 'SUCCESS', name: 'ubuntu-20.04-incremental publishChecks', status: 'COMPLETED', title: 'Build');

        } catch (Exception e) {
          e.printStackTrace();
          buildResult = "FAILURE";
          publishChecks(conclusion: 'FAILURE', name: 'ubuntu-20.04-incremental publishChecks', status: 'COMPLETED', title: 'Build');

          // githubNotify(description: "${description} - Build failed",  context: "${context}", status: "${buildResult}" , credentialsId: "${githubToken}");
          error("build step failed. check logs");
        }
      }
    }

    stage("Test") {
      dir(build_dir) {

        sh("find . -name '*.gcda'");

        try {
          sh("""
            Xvfb :99 &
            export DISPLAY=:99
            rm -rf ./Testing
            ctest -j \$(nproc) -T test --no-compress-output --output-on-failure
            """);


        } catch (Exception e) {
           // re-run failed tests
           try {
              sh("ctest -T test -j \$(nproc) --rerun-failed --no-compress-output --output-on-failure")
           } catch (Exception err_2) {
              err_2.printStackTrace();
              buildResult = "ERROR"; // Fail when retry fails. We mark it as Error to indicate the the job did complete, but exited with a non-zero status
              description = "${description} - CTest Failures";
           }

        } finally {

          try {

            archiveArtifacts artifacts: 'Testing/', fingerprint: true

            xunit (
              testTimeMargin: '15000',
              thresholdMode: 1,
              thresholds: [
                skipped(failureThreshold: '0'),
                failed(failureThreshold: '0')
              ],
            tools: [CTest(
              pattern: 'Testing/**/*.xml',
              deleteOutputFiles: true,
              failIfNotNew: false,
              skipNoTestFiles: true,
              stopProcessingIfError: false
            )])
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    }

    stage("Extract coverage result") {
      dir(build_dir) {
        sh("find . -name '*.gcda'");

        sh("rm -Rf gcov/ && mkdir -p gcov/xml gcov/html gcov/html_details");
        // Passing --root ${base_dir} is problematic apparently
        sh("""#!/bin/bash
            gcovr -j \$(nproc) --root ../ --delete \
            --exclude '.*_GTest\\.cpp' --exclude ".*wrap\\.cxx" --exclude ".*Fixture.*" --exclude ".*_Benchmark\\.cpp" \
            --exclude '.*\\.cxx' --exclude '.*\\.hxx' \
            --exclude-unreachable-branches --exclude-throw-branches \
            --print-summary \
            --xml gcov/xml/coverage.xml \
            --html gcov/html/gcov.html \
            .
            """);

        try {
          cobertura(
            coberturaReportFile: "**/gcov/xml/coverage.xml",
            enableNewApi: true,

            // Report health as 100% if line coverage > 1st
            // Report health as 0% if line coverage < 2nd
            // Mark build as unstable if line coverage < 3rd
            methodCoverageTargets: '80, 80, 0',
            lineCoverageTargets: '80, 80, 0',
            conditionalCoverageTargets: '50, 0, 0',
            // Will automatically tighten the coverage targets from build to build
            autoUpdateHealth: false,
            autoUpdateStability: false,

            failUnhealthy: true,
            failUnstable: false,
            onlyStable: false,
            maxNumberOfBuilds: 0,
            sourceEncoding: 'UTF_8',
            zoomCoverageChart: true
          );

          //def failedCoverage = publishCoverage(
            //globalThresholds: [[thresholdTarget: 'Line', unhealthyThreshold: 80.0]],
            //sourceFileResolver: sourceFiles('STORE_ALL_BUILD'),
            //calculateDiffForChangeRequests: true,
            //failBuildIfCoverageDecreasedInChangeRequest: true,
            //failUnhealthy: true,
            //adapters: [
              //coberturaAdapter(
                //path: '**/gcov/xml/coverage.xml',
                //// thresholds: [[failUnhealthy: true, thresholdTarget: 'Line', unhealthyThreshold: 70.0]]
              //)
            //],
          //);

          //if (failedCoverage) {
            //buildResult = "FAILURE";
            //description = "${description} - Coverage Failed";
          //}
        } catch (Exception e) {
          e.printStackTrace();
          buildResult = "FAILURE";
          description = "${description} - Coverage Failed";
        }

        // There should be none left...
        sh("find . -name '*.gcda'");
      }
    }

    stage ("A final stage") {
      println "Bye!";
    }
  } // end node desktop-ubuntu-1804

  if ((buildResult != "FAILURE") && (buildResult != "ERROR")) {
    buildResult = "SUCCESS"
  }

  // return status code to GitHub
  //githubNotify(description: "${description}",  context: "${context}", status: "${buildResult}" , credentialsId: "${githubToken}");
  currentBuild.result = "${buildResult}";

} // end call()
