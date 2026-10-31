import pathlib
import unittest
import xml.etree.ElementTree as ET


class PluginDescriptorTest(unittest.TestCase):
    def setUp(self):
        root = pathlib.Path(__file__).resolve().parents[1]
        descriptor = root / "src/main/resources/META-INF/plugin.xml"
        self.plugin = ET.parse(descriptor).getroot()

    def test_supports_future_ide_builds_without_an_upper_limit(self):
        idea_version = self.plugin.find("idea-version")
        self.assertIsNotNone(idea_version)
        self.assertEqual("262", idea_version.get("since-build"))
        self.assertNotIn("until-build", idea_version.attrib)

if __name__ == "__main__":
    unittest.main()
