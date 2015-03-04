from fabric.api import run, sudo, env, task, local, put, parallel
from fabric.context_managers import cd
import logging
from fabtools import require

# env.user = 'root'
env.use_ssh_config = True

PACKAGES = ['openjdk-7-jre-headless','python-pip','dstat','htop','supervisor',
            'libjna-java', 'libopts25','ntp', 'python-support',]

folder = "/opt/kinesis3"

@task
def build():
  local("lein clean")
  local("lein uberjar")

@task
@parallel
def deploy(restart=True):
  sudo("mkdir -p {}".format(folder))
  jar_file = 'kinesis3.jar'
  new_jar = 'new-' + jar_file


  with cd(folder):
    put("target/uberjar/kinesis3.jar", new_jar,use_sudo=True)
    sudo("mv {} {}".format(new_jar, jar_file))

    sudo("supervisorctl restart kinesis3")