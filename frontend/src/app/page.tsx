import HeroLanding from '../components/HeroLanding';
import SplitExperience from '../components/SplitExperience';
import ExperienceSections from '../components/ExperienceSections';

export default function Home(){
  return <>
    <HeroLanding />
    <div style={{background:'#f9f7f5',padding:'60px 20px 20px'}}><SplitExperience/></div>
    <ExperienceSections/>
  </>;
}
