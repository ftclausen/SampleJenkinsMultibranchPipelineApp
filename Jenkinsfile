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
		"one": {
			node( 'jenkins-node.example.com' ) {
				recordTestResults( {
					one()
				}, TestType.UNIT )
			}
		},
		"two": {
			node( 'jenkins-node.example.com' ) {
				recordTestResults( {
					two()
				}, TestType.E2E )
			}
		}
	)
}

node( 'jenkins-node.example.com' ) {
  stage( "Notifications" ) {
		def uniqueResults = testResults.values() as Set
		println "Unique results = $uniqueResults"
		if ( uniqueResults.size() == 1 && uniqueResults[0] == JobState.SUCCESS ) {
			println "All tests passed"
			notifySCM()
      currentBuild.result = JobState.SUCCESS.toString()
		} else {
			println "One or more tests failed, see below:"
			for( TestType testType: testResults.keySet() ) {
							println "$testType: ${testResults[testType]}"
			}
			notifySCM()
      currentBuild.result = JobState.FAILED.toString()
		}
  } 
}

// Pipeline steps

def unit() {
  // Actually run unit tests here
	sh 'sleep 5'
}

def e2e() {
  // Actually run end-to-end tests here
	sh 'sleep 30'
}

// Utils

def recordTestResults( Closure block, TestType testType ) {
  try {
    notifySCM( JobState.INPROGRESS )
    block()
    println "$testType SUCCESS, recording result"
    testResults[ testType ] = JobState.SUCCESS
  } catch ( error ) {
    println "$testType FAILED, recording result"
    testResults[ testType ] = JobState.FAILED
  } finally {
    try {
      publishTestResults()
    } finally {
      notifySCM()
    }
  }
}

def notifySCM() {
  // Call an SCM notifier plugin here e.g. Github or Bitbucket
  println "This is where we notify the SCM platform e.g. Github"
}

def pushlishTestResults() {
}
