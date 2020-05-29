//Define your Ant Version here
def antVersion = 'Ant1.9.0' 
pipeline {
    agent any
    options {
	//State the number of rotations of your pipeline you want to keep, the below will keep the last 25. 
        buildDiscarder(logRotator(numToKeepStr: '25'))
	//If the pipeline has been running for 45 minutes it will timeout
        timeout(time: 45, unit: 'MINUTES')
    }
	//The various stages of the pipeline are defined within the stages block
	//Each stage can have a set of steps
    stages {
	//A stage that handles an SVN checkout
        stage('Checkout from SVN') {
            steps {
                checkout([$class: 'SubversionSCM',
                    additionalCredentials: [],
                    excludedCommitMessages: '',
                    excludedRegions: '',
                    excludedRevprop: '',
                    excludedUsers: '',
                    filterChangelog: false,
                    ignoreDirPropChanges: false,
                    includedRegions: '',
                    locations: [
                        [cancelProcessOnExternalsFail: false,
                            credentialsId: '*INSERT YOUR CREDENTIALS HERE*',
                            depthOption: 'infinity',
                            ignoreExternalsOption: true,
                            local: '.',
                            remote: '*INSERT URL TO YOUR SVN REPOSITORY HERE*'
                        ]
                    ],
                    quietOperation: false,
                    workspaceUpdater: [$class: 'UpdateWithCleanUpdater']
                ])
            }
        }
        stage('Build') {
            steps {
		//Tells Jenkins which environment to run the following task with
                withEnv(["ANT_HOME=${tool antVersion}"]) {
		    //Adds a label to the console then runs a predefined Ant build task from the Ant build file. Make sure your path to the build.xml file is correct.
                    labelledShell label: 'Executing ANT Task - build', script: 'ant build -f build.xml'
                }
                labelledShell label: 'Archive the artifacts', script: 'echo Archive the artifacts'
                archiveArtifacts 'build/*'
            }
        }
        stage('Jacoco Coverage') {
            steps {
                withEnv(["ANT_HOME=${tool antVersion}"]) {
		    //Adds a label to the console then runs a predefined Ant jacoco_report task from the Ant build file. Make sure your path to the build.xml file is correct.
                    labelledShell label: 'Calculating Coverage and generating Jacoco report - ANT TASK: jacoco_report', script: 'ant jacoco_report -f build.xml'
                }
                jacoco classPattern: '*INSERT CLASS PATTERN HERE*',
                    deltaBranchCoverage: '80',
                    deltaClassCoverage: '80',
                    deltaComplexityCoverage: '80',
                    deltaInstructionCoverage: '80',
                    deltaLineCoverage: '80',
                    deltaMethodCoverage: '80',
                    exclusionPattern: '**/*Test*.class',
                    inclusionPattern: '**/*.class',
                    maximumBranchCoverage: '80',
                    maximumClassCoverage: '80',
                    maximumComplexityCoverage: '80',
                    maximumInstructionCoverage: '80',
                    maximumLineCoverage: '80',
                    maximumMethodCoverage: '80',
                    minimumBranchCoverage: '80',
                    minimumClassCoverage: '80',
                    minimumComplexityCoverage: '80',
                    minimumInstructionCoverage: '80',
                    minimumLineCoverage: '80',
                    minimumMethodCoverage: '80',
                    sourceExclusionPattern: '**/*Test*.java',
                    sourcePattern: '*INSERT SOURCE PATTERN HERE*'
            }
        }
        stage('Run SonarQube scan') {
            steps {
		//A SonarQube Environment is defined in your Jenkins configuration 
                withSonarQubeEnv('SonarQube Environment') {
                    withEnv(["ANT_HOME=${tool antVersion}"]) {
			//Performs the Sonar task and sends to Sonar server
                        labelledShell label: 'Executing Sonar Scan and submitting report to - *SonarQube Server* ANT TASK: sonar', script: 'ant sonar -Dsonar.projectKey="*INSERT PROJECT KEY*" -Dsonar.host.url=*INSERT SERVER URL* -Dsonar.login="*LOGIN CREDENTIAL*"  -f build.xml'
                    }
                }
            }
        }
        stage("Wait for Sonar Quality Gate") {
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    labelledShell label: 'Waiting for Sonar Quality Gate to complete.', script: 'echo Waiting for Sonar Quality Gate to complete.'
		    //The waitForQualityGate step waits for the Sonar server to conclude its analysis, there will be a standard or predefined Quality Gate.
		    //For example 80% code coverage, if this fails then the Pipeline will fail. 
                    waitForQualityGate abortPipeline: true
                    labelledShell label: 'Quality gate is OK', script: 'echo Quality gate is OK'
                }
            }
        }
    }
    //The post stages handle the various scenarios when the stages finish
    post {
	//These tasks will always run, regardless of pipline result. 
        always {
            junit '**/TestReports/*.xml'
            labelledShell label: 'Archiving Unit test reports', script: 'echo Archiving Unit test reports'
        }
	//If pipeline runs successfully, do the following...
        success {
            script {
            }
        }
	//If pipeline fails, do the following...
        failure {
           mail to: "*INSERT EMAIL ADDRESS HERE*", subject: "FAILURE: ${currentBuild.fullDisplayName}", body: "Pipeline Failed."
        }
	//If pipeline is unstable, do the following...
        unstable {
            mail to: "*INSERT EMAIL ADDRESS HERE*", subject: "Unstable: ${currentBuild.fullDisplayName}", body: "Pipeline Unstable."
        }
    }
}
