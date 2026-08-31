require "minitest/autorun"
require "yaml"

ROOT = File.expand_path("../../..", __dir__)

class WorkflowPolicyTest < Minitest::Test
  def load_workflow(name)
    YAML.safe_load(File.read(File.join(ROOT, ".github/workflows", name)), aliases: true)
  end

  def test_manual_and_tag_lanes_share_build_but_only_tags_release
    workflow = load_workflow("publish-tv.yaml")
    triggers = workflow.fetch(true)
    assert triggers.key?("workflow_dispatch")
    assert_equal ["v*-atv.*"], triggers.fetch("push").fetch("tags")
    assert_equal "read", workflow.fetch("permissions").fetch("contents")
    jobs = workflow.fetch("jobs")
    assert_equal "write", jobs.fetch("release").fetch("permissions").fetch("contents")
    assert_includes jobs.fetch("release").fetch("if"), "refs/tags/"
    build_steps = jobs.fetch("build").fetch("steps")
    assert build_steps.any? { |step| step["run"]&.include?("build-findroid-tv-release.sh") }
    assert build_steps.any? { |step| step["run"]&.include?("verify-findroid-tv-release.sh") }
    assert build_steps.any? { |step| step.fetch("uses", "").start_with?("actions/upload-artifact@") }
    release_steps = jobs.fetch("release").fetch("steps")
    release = release_steps.find { |step| step.fetch("uses", "").start_with?("softprops/action-gh-release@") }
    assert_equal true, release.fetch("with").fetch("draft")
    assert_equal true, release.fetch("with").fetch("prerelease")
    assert release_steps.any? { |step| step["run"]&.include?("CERTIFICATE_SHA256_PLACEHOLDER") }
  end

  def test_upstream_publish_job_is_repository_guarded
    workflow = load_workflow("publish.yaml")
    assert_equal "github.repository == 'jarnedemeulemeester/findroid'", workflow.fetch("jobs").fetch("publish").fetch("if")
  end
end
