packages = {
    "/usr/lib/jvm/java-1.7.0/bin/java -version 2>&1" => "1.7.0",
    "java -version 2>&1" => "1.8.0",
    "node -v" => "v6.9.0",
    "mvn -v" => "3",
    "inspec -v" => "InSpec",
    "packer version" => "1.2.1",
    "sudo docker version" => "17.12.1-ce",
    "kubectl version --client" => "Client Version",
    "ansible --version" => "2",
    "/usr/local/bin/compass -v" => "1.0.3",
    "/usr/local/bin/sass -v" => "3.5.5",
    "ruby -v" => "2.0",
    "bash --version" => "4."
}

packages.each do |pkg,out|
    describe command(pkg) do
        its('stdout') {should include(out) }
        its('stderr') {should eq ''}
        its('exit_status') {should eq 0}
    end
end