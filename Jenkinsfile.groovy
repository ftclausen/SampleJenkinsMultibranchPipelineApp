// -*- mode: groovy -*-
// vim: set filetype=groovy :
import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateAction

enum TestType { UNIT, E2E }
enum JobState { INPROGRESS, SUCCESS, FAILED }
testResults = [
  (TestType.UNIT): JobState.INPROGRESS,
  (TestType.E2E): JobState.INPROGRESS
]

envVars = [
  'DISPLAY=:0',
  'LD_LIBRARY_PATH=/opt/google/chrome/lib'
] 

withE2EPod {
  gitCheckout( true )
  recordTestResults(TestType.UNIT) {
    sh 'echo "Hello"'
    // prepare()
    // unit()
  }
}
/*
stage( 'Unit and E2E Tests' ) {
  parallel(
    "Unit": {
      withE2EPod {
        gitCheckout( true )
        recordTestResults(TestType.UNIT) {
          prepare()
          unit()
        }
      }
    },
    "E2E": {
      node( 'ultra-e2e-v2' ) {
        withEnv(envVars) {
          gitCheckout( false )
          recordTestResults(TestType.E2E) {
            prepare()
            e2e()
          }
        }
      }
    }
  )
}
*/

// Pipeline steps

def prepare() {
  sh '''#!/usr/bin/env bash
  set -e
  export NVM_DIR="$HOME/.nvm"
  # Don't do this in prod - use container images with Node already configured
  curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.33.2/install.sh | bash
  source ~/.nvm/nvm.sh
  nvm install 8.11.3
  npm install
  ./node_modules/.bin/webdriver-manager update
  nohup ./node_modules/.bin/webdriver-manager start &
  nohup ./node_modules/.bin/yarn start &
  ./start_xvfb.sh
  ./wait_for_webdriver_manager.sh
  '''
}
def unit() {
  // Actually run unit tests here
  sh '''#!/usr/bin/env bash
    set -e
    source ~/.nvm/nvm.sh
    ./node_modules/.bin/yarn run test
  '''
}

def e2e() {
  // Actually run end-to-end tests here
  sh '''#!/usr/bin/env bash
    set -e
    source ~/.nvm/nvm.sh
    ps auxww | grep selenium-server
    ./node_modules/.bin/protractor test/e2e/conf.js
  '''
}

def withE2EPod( block ) {
  clearPodTemplateNames()
  def podLabel = uniquePodLabel('example-pipeline')
  def jenkinsWorkspace = '/home/jenkins/agent'

  podTemplate(
    name: podLabel,
    label: podLabel,
    instanceCap: 1,
    nodeUsageMode: 'EXCLUSIVE',
    // And add any pod annotations or service account details you may require here
    volumes: [
      // Chrome needs much more than the default 64M shared memory so mount /dev/shm to memory
      emptyDirVolume( mountPath: '/dev/shm', memory: true )
    ]) {
    node(podLabel) {
        sh 'echo "Hello from a container"'
    }
  }
}

// Utils

/*
 * Workaround from https://issues.jenkins-ci.org/browse/JENKINS-42184. Without
 * this, weird things happen when firing up multiple pods for a single parallel
 * build.
 */
def clearPodTemplateNames() {
  def action = currentBuild.rawBuild.getAction( PodTemplateAction.class )
  if ( action ) {
    // Before kubernetes-plugin 1.1.2 this field was called 'names'
    def previousTemplates = action.hasProperty( 'names' ) ? action.names : action.stack
    previousTemplates.clear()
  }
}

/*
 * Jenkins appears to cache podTemplates, ignoring definition changes.  Also, Jenkins is slow to start new pods if
 * another one with the same label already exists.  Force a unique name for each template.
 */
def uniquePodLabel( label ) {
  def uuid = UUID.randomUUID().toString().replaceAll( '-', '' ).take( 6 )
  return "$label-$uuid"
}

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

def notifyStash() {
  println "NOTIFY: Sending notification of: ${currentBuild.result}"
  step( [ $class: 'StashNotifier', credentialsId: 'jenkins-notify' ] )
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
    browser: [ $class: 'GitHub', repoUrl: '' ],
    extensions: [
      [ $class: 'CloneOption', noTags: true ],
      [ $class: 'PruneStaleBranch' ],
      [ $class: 'LocalBranch', localBranch: env.BRANCH_NAME ],
    ],
    userRemoteConfigs: [[
      url: '',
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
