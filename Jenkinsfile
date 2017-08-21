// -*- mode: groovy -*-
// vim: set filetype=groovy :

enum TestType { UNIT, E2E }
enum JobState { INPROGRESS, SUCCESS, FAILED }
testResults = [
  (TestType.UNIT): JobState.INPROGRESS,
  (TestType.E2E): JobState.INPROGRESS
]

stage( 'Unit and E2E Tests' ) {
  parallel(
    /*
    "Unit": {
      node( 'ultra-e2e-v2' ) {
        gitCheckout( true )
        recordTestResults(TestType.UNIT) {
          unit()
        }
      }
    },
    */
    "E2E": {
      node( 'ultra-commit-pipeline-v3' ) {
        gitCheckout( false )
        recordTestResults(TestType.E2E) {
          prepare()
          e2e()
        }
      }
    }
  )
}

// Pipeline steps

def prepare() {
  sh '''#!/usr/bin/env bash
  export NVM_DIR="$HOME/.nvm"
  # Don't do this in prod - use container images with Node already configured
  curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.33.2/install.sh | bash
  source ~/.nvm/nvm.sh
  nvm install 6.11.2
  npm install
  ./node_modules/.bin/webdriver-manager update
  nohup ./node_modules/.bin/webdriver-manager start &
  nohup ./node_modules/.bin/yarn start &
  ./start_xvfb.sh
  '''
}
def unit() {
  // Actually run unit tests here
  sh '''#!/usr/bin/env bash
    source ~/.nvm/nvm.sh
    ./node_modules/.bin/yarn test
  '''
}

def e2e() {
  // Actually run end-to-end tests here
  sh '''#!/usr/bin/env bash
    source ~/.nvm/nvm.sh
    echo "====> Sleeping..."
    sleep 10
    ps auxww | grep selenium-server
    ./node_modules/.bin/protractor test/e2e/conf.js
  '''
}

// Utils

def recordTestResults( TestType testType, Closure block ) {
  try {
    notifyStash()
    block()
    println "$testType SUCCESS, recording result"
    testResults[ testType ] = JobState.SUCCESS
  } catch ( error ) {
    println "$testType FAILED, recording result, error follows: ${error.printStackTrace()}"
    testResults[ testType ] = JobState.FAILED
  } finally {
    try {
      // Publish results here
    } finally {
      sendNotifications()
    }
  }
}

def sendNotifications() {
  println "DEBUG: Job states: ${testResults}"
  if ( testResults.values().contains( JobState.INPROGRESS ) ) {
    println "DEBUG: A test is still in progress, not sending notifications yet. States: $testResults"
    return
  }

  node( 'notifications-only' ) {
    stage( "Notifications" ) {
      def uniqueResults = testResults.values() as Set
      println "DEBUG: Unique results = $uniqueResults"
      if ( uniqueResults.size() == 1 && uniqueResults[0] == JobState.SUCCESS ) {
        println "SUCCESS: All tests passed"
        currentBuild.result = JobState.SUCCESS.toString()
      } else {
        currentBuild.result = JobState.FAILED.toString()
        println "One or more tests failed: $testResults"
      }
      notifyStash()
      notifySlack()
    }
  }
}

def notifyStash() {
  println "NOTIFY: Sending notification of: ${currentBuild.result}"
  // step( [ $class: 'StashNotifier', credentialsId: 'jenkins-notify' ] )
}

def notifySlack() {
  def message = "${currentBuild.result}: Job '${env.JOB_NAME}, ${env.BUILD_URL}"
  def color = '#FF0000' // Red
  def currentBuildResult = currentBuild.rawBuild.getResult()
  def previousBuildResult = currentBuild.rawBuild.getPreviousBuild()?.getResult()

  if (currentBuildResult.equals(hudson.model.Result.SUCCESS) &&
      !previousBuildResult.equals(hudson.model.Result.SUCCESS)) {
    color = '#33CC00' // Green
    message = "Back to normal: ${message}"    
  } else if (currentBuildResult.equals(hudson.model.Result.SUCCESS)) {
    return
  }

  slackSend color: color,
            message: message,
            channel: '#notification-tests'
}

def gitCheckout( showInChangelog ) {
  checkout changelog: showInChangelog, scm: [
    $class: 'GitSCM',
    branches: [[ name: env.BRANCH_NAME ]],
    browser: [ $class: 'Stash', repoUrl: 'ssh://stash.bbpd.io/~fclausen/samplejenkinsmultibranchpipelineapp.git' ],
    extensions: [
      [ $class: 'CloneOption', noTags: true ],
      [ $class: 'PruneStaleBranch' ],
      [ $class: 'LocalBranch', localBranch: env.BRANCH_NAME ],
    ],
    userRemoteConfigs: [[
      credentialsId: 'jenkins-stash',
      url: 'ssh://stash.bbpd.io/~fclausen/samplejenkinsmultibranchpipelineapp.git',
    ]]
  ]
}

/**
 * Gets the current Jenkins build object
 */
@NonCPS
def currentJenkinsBuild() {
  def job = Jenkins.instance.getItemByFullName( env.JOB_NAME )
  return job.getBuild( env.BUILD_ID )
}
