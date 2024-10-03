# AWS Car/Text Recognition
Image recognition pipeline in AWS, using two EC2 instances, S3, SQS, and Rekognition.

--Set Up:
- Launch AWS from Learner Lab (https://awsacademy.instructure.com/courses/93346)
- Dowloand .pem key (for mac/linux) or .ppk key (for windows) from Learner Lab Console
    - (change key name to vockey and chmod 400)

--Instance Creation:
- Create two instances (instanceA and instanceB)
    - Select "Launch Instances"
    - Under "Application and OS Images", select "Amazon Linux AMI" 
    - Under "Instance type", select "t2.micro"
    - For "Key pair (login)", select "vockey"
    - Under "Network settings", click edit and create 3 security group rules (ssh, HTTP, HTTPS all with source as My IP)
    - Click "Launch Instance"

--EC2 Instances Setup:
- Connect to instances by running the following command in terminal from the directory where the key is stored:
     - (ssh -i "vockey.pem" ec2-user@<YOUR_EC2_INSTANCE_PUBLIC_IPV4_ADDRESS>
- Set up AWS Credentials
    - make a .aws directory (mkdir .aws)
    - cd into directory and create a credentials and config file
         - cd .aws
         - vim credentials
              - copy and paste AWS Details from learner lab
                   - [default]
                   - aws_access_key_id=yourAccessKeyId
                   - aws_secret_access_key=yourSecretAccessKey
                   - aws_session_token=yourSessionToken

         - vim config
              - copy and paste the following:
                   - [default]
                   - region = us-east-1
                
- Run the following commands:
    (Install java, dependencies, and other packages)
    - sudo yum install java-1.8.0-devel
    - sudo /usr/sbin/alternatives --config java
         - Enter the number that corresponds to java-1.8.0-openjdk.x86_64
    - sudo yum install git -y

--Run Application
- git clone https://github.com/sakthivelanm/AWS_CarText_Recognition.git
- on instance A, run:
    java -jar AWS_CarText_Recognition/carRecognition/carRecognition.jar
- on instance B, run
    java -jar AWS_CarText_Recognition/textRecognition/textRecognition.jar
- either program can be run first, output will be in textRecognition directory


