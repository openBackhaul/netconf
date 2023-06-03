# v5.0.6-SR3

This release contains v5.0.6 + cherry-picks:

1) https://git.opendaylight.org/gerrit/c/netconf/+/103299/8

and these changes:

1) changed sshd version to be 2.8.0 by reverting bump commits
2) disabled transport build as transport-ssh is entirely based on the mina sshd 2.9.x
